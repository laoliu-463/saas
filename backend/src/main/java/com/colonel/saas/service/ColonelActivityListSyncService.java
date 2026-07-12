package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelActivitySyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelActivitySyncJobLogMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 活动列表异步同步服务。
 * <p>
 * 将抖店活动列表（状态、名称、时间窗口等元数据）的同步从商品同步流程中解耦。
 * 活动列表同步有独立的 job 日志表、独立的锁和独立的时间字段（{@code activity_status_synced_at}）。
 * </p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>异步触发：前端 POST → 写 QUEUED job → 异步线程执行 → 前端轮询 GET</li>
 *   <li>锁互斥：使用 {@code COLONEL_ACTIVITY_LIST_SYNC} Redis 锁，与商品同步锁独立</li>
 *   <li>时间隔离：更新 {@code activity_status_synced_at}，不影响商品同步的 {@code last_sync_at}</li>
 *   <li>陈旧任务 reconcile：定时将超时的 RUNNING/QUEUED 标记为 ABANDONED</li>
 * </ul>
 */
@Slf4j
@Service
public class ColonelActivityListSyncService {

    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(10);
    private static final Duration STALE_JOB_THRESHOLD = Duration.ofMinutes(30);

    private final DouyinActivityGateway douyinActivityGateway;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ColonelActivitySyncJobLogMapper jobLogMapper;
    private final ColonelsettlementActivityService activityService;
    private final DistributedJobLockService jobLockService;
    private final ExecutorService syncExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${colonel.activity.list-sync.enabled:true}")
    private boolean enabled;

    @Value("${colonel.activity.list-sync.max-pages:50}")
    private int maxPages;

    @Value("${colonel.activity.list-sync.page-size:20}")
    private int pageSize;

    public ColonelActivityListSyncService(
            DouyinActivityGateway douyinActivityGateway,
            ColonelsettlementActivityMapper activityMapper,
            ColonelActivitySyncJobLogMapper jobLogMapper,
            ColonelsettlementActivityService activityService,
            DistributedJobLockService jobLockService) {
        this.douyinActivityGateway = douyinActivityGateway;
        this.activityMapper = activityMapper;
        this.jobLogMapper = jobLogMapper;
        this.activityService = activityService;
        this.jobLockService = jobLockService;
        this.syncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "activity-list-sync");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 触发活动列表异步同步。
     *
     * @param triggeredBy 触发人用户 ID（可为 null）
     * @return 同步结果，包含 jobId 和当前状态
     */
    public SyncTriggerResult triggerSync(UUID triggeredBy) {
        if (!enabled) {
            return new SyncTriggerResult(null, "DISABLED", "活动列表同步已关闭");
        }
        if (running.get()) {
            return new SyncTriggerResult(null, "RUNNING", "上一次活动列表同步尚未完成，请稍后查看");
        }

        String jobId = "act-list-" + UUID.randomUUID().toString().substring(0, 8);
        ColonelActivitySyncJobLog jobLog = new ColonelActivitySyncJobLog();
        jobLog.setId(UUID.randomUUID());
        jobLog.setJobId(jobId);
        jobLog.setSyncType("ACTIVITY_LIST");
        jobLog.setStatus("QUEUED");
        jobLog.setTriggeredBy(triggeredBy);
        jobLog.setActivitiesTotal(0);
        jobLog.setActivitiesSynced(0);
        jobLog.setActivitiesFailed(0);
        jobLog.setCreateTime(LocalDateTime.now());
        jobLog.setUpdateTime(LocalDateTime.now());
        jobLog.setDeleted(0);
        jobLogMapper.insert(jobLog);

        syncExecutor.submit(() -> executeSync(jobId));
        return new SyncTriggerResult(jobId, "QUEUED", null);
    }

    /**
     * 查询同步任务状态。
     */
    public SyncJobStatus getJobStatus(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return null;
        }
        ColonelActivitySyncJobLog jobLog = jobLogMapper.selectByJobId(jobId);
        if (jobLog == null) {
            return null;
        }
        return new SyncJobStatus(
                jobLog.getJobId(),
                jobLog.getStatus(),
                jobLog.getActivitiesTotal(),
                jobLog.getActivitiesSynced(),
                jobLog.getActivitiesFailed(),
                jobLog.getStartedAt() == null ? null : jobLog.getStartedAt().toString(),
                jobLog.getFinishedAt() == null ? null : jobLog.getFinishedAt().toString(),
                jobLog.getErrorMessage());
    }

    private void executeSync(String jobId) {
        String lockOwner = "act-list-sync:" + jobId;
        if (!jobLockService.tryAcquire(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, SYNC_LOCK_TTL, lockOwner)) {
            log.info("ColonelActivityListSync skipped, lock held, jobId={}", jobId);
            jobLogMapper.updateStatus(jobId, "FAILED_LOCKED", LocalDateTime.now(), 0, 0, "同步锁被占用", null);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            jobLockService.releaseWithOwner(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner);
            jobLogMapper.updateStatus(jobId, "FAILED_LOCKED", LocalDateTime.now(), 0, 0, "并发冲突", null);
            return;
        }
        try {
            jobLogMapper.updateRunning(jobId, LocalDateTime.now());
            int synced = 0;
            int failed = 0;
            int total = 0;

            for (int page = 1; page <= maxPages; page++) {
                DouyinActivityGateway.ActivityListResult result;
                try {
                    result = douyinActivityGateway.listActivities(
                            new DouyinActivityGateway.ActivityListQuery(null, null, null, null, (long) page, (long) pageSize, null));
                } catch (Exception ex) {
                    log.warn("ColonelActivityListSync page fetch failed, jobId={}, page={}", jobId, page, ex);
                    failed++;
                    break;
                }

                if (result == null || result.activityList() == null || result.activityList().isEmpty()) {
                    break;
                }

                total += result.activityList().size();
                for (DouyinActivityGateway.ActivityItem item : result.activityList()) {
                    try {
                        activityService.syncFromGatewayItem(item);
                        synced++;
                    } catch (Exception ex) {
                        log.warn("ColonelActivityListSync item failed, jobId={}, activityId={}", jobId, item.activityId(), ex);
                        failed++;
                    }
                }

                // 续租
                jobLockService.renew(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner, SYNC_LOCK_TTL.toMillis());

                if (result.activityList().size() < pageSize) {
                    break;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            String status = failed == 0 ? "SUCCESS" : (synced > 0 ? "PARTIAL" : "FAILED");
            String errorMessage = failed > 0 ? String.format("同步失败 %d 条", failed) : null;
            jobLogMapper.updateStatus(jobId, status, LocalDateTime.now(), synced, failed, errorMessage,
                    toMetadataJson(total, synced, failed));
            log.info("ColonelActivityListSync finished, jobId={}, status={}, total={}, synced={}, failed={}",
                    jobId, status, total, synced, failed);
        } catch (Exception ex) {
            log.error("ColonelActivityListSync unexpected error, jobId={}", jobId, ex);
            jobLogMapper.updateStatus(jobId, "FAILED", LocalDateTime.now(), 0, 0, ex.getMessage(), null);
        } finally {
            running.set(false);
            jobLockService.releaseWithOwner(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner);
        }
    }

    /**
     * 定时 reconcile 陈旧任务。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void reconcileStaleJobs() {
        try {
            LocalDateTime staleBefore = LocalDateTime.now().minus(STALE_JOB_THRESHOLD);
            int reconciled = jobLogMapper.reconcileStaleJobs(staleBefore);
            if (reconciled > 0) {
                log.info("ColonelActivityListSync reconciled {} stale jobs", reconciled);
            }
        } catch (Exception ex) {
            log.warn("ColonelActivityListSync stale job reconcile failed", ex);
        }
    }

    private String toMetadataJson(int total, int synced, int failed) {
        return String.format("{\"total\":%d,\"synced\":%d,\"failed\":%d}", total, synced, failed);
    }

    public record SyncTriggerResult(String jobId, String status, String message) {}

    public record SyncJobStatus(
            String jobId,
            String status,
            int activitiesTotal,
            int activitiesSynced,
            int activitiesFailed,
            String startedAt,
            String finishedAt,
            String errorMessage) {}
}
