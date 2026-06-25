package com.colonel.saas.job;

import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.colonel.saas.service.DistributedJobLockService;
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

    private final ProductSyncJobLogMapper jobLogMapper;
    private final DistributedJobLockService jobLockService;
    private final int runningTimeoutMinutes;

    public StaleProductSyncJobReconcileJob(
            ProductSyncJobLogMapper jobLogMapper,
            DistributedJobLockService jobLockService,
            @Value("${product.sync.backfill.runningTimeoutMinutes:30}") int runningTimeoutMinutes) {
        this.jobLogMapper = jobLogMapper;
        this.jobLockService = jobLockService;
        this.runningTimeoutMinutes = Math.max(1, runningTimeoutMinutes);
    }

    @Scheduled(cron = "${product.sync.backfill.staleReconcileCron:0 */15 * * * ?}")
    public void reconcile() {
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, LOCK_TTL)) {
            log.debug("StaleProductSyncJobReconcileJob skipped, lock held");
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(runningTimeoutMinutes);
            List<ProductSyncJobLog> stale = jobLogMapper.selectStaleRunningJobs(threshold);
            if (stale.isEmpty()) {
                return;
            }
            int abandoned = 0;
            for (ProductSyncJobLog job : stale) {
                int rows = jobLogMapper.abandonStaleRunningJob(job.getId(), LocalDateTime.now());
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
}
