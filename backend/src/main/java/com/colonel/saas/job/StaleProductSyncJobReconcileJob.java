package com.colonel.saas.job;

import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.colonel.saas.service.DistributedConcurrencyLimiter;
import com.colonel.saas.service.DistributedJobLockService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品同步任务日志 stale RUNNING 清理任务。
 *
 * <p>Phase 4-1.5 deadlock 修复配套：定时检查 {@code product_sync_job_log} 里
 * {@code status = 'RUNNING'} 但 {@code started_at} 超过阈值的僵尸 job，标记为
 * {@code ABANDONED} 并写 {@code finished_at}。不删除任何业务事实数据。</p>
 *
 * <p>使用 Redis 锁避免多实例重复清理同一批僵尸 job。</p>
 */
@Slf4j
@Component
public class StaleProductSyncJobReconcileJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final String MANUAL_ACTIVITY_SYNC_JOB_TYPE = "activity_product_manual_sync";
    private static final String ACTIVITY_SCOPE_PREFIX = "ACTIVITY:";

    private final ProductSyncJobLogMapper jobLogMapper;
    private final DistributedJobLockService jobLockService;
    private final DistributedConcurrencyLimiter concurrencyLimiter;
    private final int runningTimeoutMinutes;

    public StaleProductSyncJobReconcileJob(
            ProductSyncJobLogMapper jobLogMapper,
            DistributedJobLockService jobLockService,
            @Value("${product.sync.backfill.runningTimeoutMinutes:30}") int runningTimeoutMinutes) {
        this(jobLogMapper, jobLockService, runningTimeoutMinutes, null);
    }

    @Autowired
    public StaleProductSyncJobReconcileJob(
            ProductSyncJobLogMapper jobLogMapper,
            DistributedJobLockService jobLockService,
            @Value("${product.sync.backfill.runningTimeoutMinutes:30}") int runningTimeoutMinutes,
            DistributedConcurrencyLimiter concurrencyLimiter) {
        this.jobLogMapper = jobLogMapper;
        this.jobLockService = jobLockService;
        this.concurrencyLimiter = concurrencyLimiter;
        this.runningTimeoutMinutes = Math.max(1, runningTimeoutMinutes);
    }

    @Scheduled(cron = "${product.sync.backfill.staleReconcileCron:0 */15 * * * ?}")
    public void reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(runningTimeoutMinutes);
        LocalDateTime reconciledAt = LocalDateTime.now();
        List<ProductSyncJobLog> staleBeforeLock = jobLogMapper.selectStaleRunningJobs(threshold);
        reconcileStaleManualJobs(staleBeforeLock, reconciledAt);

        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, LOCK_TTL)) {
            log.debug("StaleProductSyncJobReconcileJob skipped, lock held");
            return;
        }
        try {
            List<ProductSyncJobLog> stale = jobLogMapper.selectStaleRunningJobs(threshold);
            if (stale.isEmpty()) {
                return;
            }
            int abandoned = 0;
            for (ProductSyncJobLog job : stale) {
                int rows = jobLogMapper.abandonStaleRunningJob(job.getId(), reconciledAt);
                if (rows > 0) {
                    abandoned++;
                    log.warn("StaleProductSyncJobReconcileJob abandoned stale RUNNING job, jobId={}, startedAt={}, ageMinutes={}",
                            job.getJobId(), job.getStartedAt(), runningTimeoutMinutes);
                }
            }
            log.info("StaleProductSyncJobReconcileJob finished, threshold={}, scanned={}, abandoned={}",
                    threshold, stale.size(), abandoned);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
        }
    }

    /**
     * 手动活动商品同步会持有活动锁和并发槽；若 JVM 在任务执行中重启，原有 worker 不再执行 finally，
     * 这些资源必须由独立的 stale 通道回收，不能依赖原任务再次运行。
     * 这里先原子收敛陈旧手动任务，再按 owner 释放它自己的并发槽和活动锁，绝不删除其他任务的锁。
     */
    private void reconcileStaleManualJobs(List<ProductSyncJobLog> staleJobs, LocalDateTime reconciledAt) {
        if (staleJobs == null || staleJobs.isEmpty()) {
            return;
        }
        for (ProductSyncJobLog job : staleJobs) {
            ManualSyncLockOwner owner = manualSyncLockOwner(job);
            if (owner == null) {
                continue;
            }
            int rows = jobLogMapper.abandonStaleRunningJob(job.getId(), reconciledAt);
            if (rows <= 0) {
                continue;
            }
            try {
                jobLockService.releaseWithOwner(JobLockKeys.productBackfillActivityLock(owner.activityId()), owner.value());
            } catch (RuntimeException ex) {
                log.warn("StaleProductSyncJobReconcileJob failed to release activity lock, jobId={}, activityId={}, message={}",
                        job.getJobId(), owner.activityId(), ex.getMessage());
            }
            if (concurrencyLimiter != null) {
                try {
                    concurrencyLimiter.release(owner.value());
                } catch (RuntimeException ex) {
                    log.warn("StaleProductSyncJobReconcileJob failed to release concurrency slot, jobId={}, activityId={}, message={}",
                            job.getJobId(), owner.activityId(), ex.getMessage());
                }
            }
            log.warn(
                    "StaleProductSyncJobReconcileJob abandoned stale manual job and released owned locks, jobId={}, activityId={}, startedAt={}, ageMinutes={}",
                    job.getJobId(),
                    owner.activityId(),
                    job.getStartedAt(),
                    runningTimeoutMinutes);
        }
    }

    private ManualSyncLockOwner manualSyncLockOwner(ProductSyncJobLog job) {
        if (job == null
                || !MANUAL_ACTIVITY_SYNC_JOB_TYPE.equals(job.getJobType())
                || job.getJobId() == null
                || job.getScope() == null
                || !job.getScope().startsWith(ACTIVITY_SCOPE_PREFIX)) {
            return null;
        }
        String activityId = job.getScope().substring(ACTIVITY_SCOPE_PREFIX.length()).trim();
        if (activityId.isEmpty()) {
            return null;
        }
        return new ManualSyncLockOwner(
                job.getJobId(),
                activityId,
                "manual:" + job.getJobId() + ":activity:" + activityId);
    }

    private record ManualSyncLockOwner(String jobId, String activityId, String value) {
    }
}
