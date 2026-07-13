package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelActivitySyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelActivitySyncJobLogMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Object pageFetchExecutorMonitor = new Object();
    private volatile ExecutorService pageFetchExecutor;

    @Value("${colonel.activity.list-sync.enabled:true}")
    private boolean enabled;

    @Value("${colonel.activity.list-sync.max-pages:50}")
    private int maxPages;

    @Value("${colonel.activity.list-sync.page-size:20}")
    private int pageSize;

    @Value("${colonel.activity.list-sync.page-parallelism:4}")
    private int pageParallelism = 4;

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
            return SyncTriggerResult.disabled();
        }
        if (running.get()) {
            return SyncTriggerResult.running();
        }

        String jobId = "act-list-" + UUID.randomUUID().toString().substring(0, 8);
        String scope = "ACTIVITY_LIST_GLOBAL";  // 活动列表同步全局唯一
        // P8.4 修复: 原子 claim 活跃任务 (PostgreSQL ON CONFLICT DO NOTHING)
        // 配合 partial unique index idx_colonel_activity_sync_job_log_active_scope
        boolean claimed = jobLogMapper.tryClaimActiveJob(
                jobId, "ACTIVITY_LIST", scope, triggeredBy, LocalDateTime.now());
        if (!claimed) {
            // 同 scope 已有活跃任务, 返回 RUNNING 提示 (前端调 GET 查最新)
            return SyncTriggerResult.running();
        }

        syncExecutor.submit(() -> executeSync(jobId));
        return SyncTriggerResult.queued(jobId);
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
            jobLogMapper.updateStatus(jobId, "FAILED_LOCKED", LocalDateTime.now(), 0, 0, 0, "同步锁被占用", null);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            jobLockService.releaseWithOwner(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner);
            jobLogMapper.updateStatus(jobId, "FAILED_LOCKED", LocalDateTime.now(), 0, 0, 0, "并发冲突", null);
            return;
        }
        try {
            jobLogMapper.updateRunning(jobId, LocalDateTime.now());
            SyncAccumulator accumulator = syncActivityPages(jobId, lockOwner);
            String status = accumulator.isSuccessful()
                    ? "SUCCESS"
                    : (accumulator.synced > 0 ? "PARTIAL" : "FAILED");
            String errorMessage = accumulator.errorMessage;
            jobLogMapper.updateStatus(jobId, status, LocalDateTime.now(), accumulator.fetchedRows,
                    accumulator.synced, accumulator.failed, errorMessage,
                    toMetadataJson(accumulator.fetchedRows, accumulator.synced, accumulator.failed,
                            accumulator.pagesFetched, accumulator.complete));
            log.info("ColonelActivityListSync finished, jobId={}, status={}, total={}, pagesFetched={}, synced={}, failed={}, parallelism={}",
                    jobId, status, accumulator.fetchedRows, accumulator.pagesFetched, accumulator.synced,
                    accumulator.failed, normalizedPageParallelism());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            jobLogMapper.updateStatus(jobId, "FAILED", LocalDateTime.now(), 0, 0, 0,
                    "活动列表同步被中断", null);
        } catch (Exception ex) {
            log.error("ColonelActivityListSync unexpected error, jobId={}", jobId, ex);
            jobLogMapper.updateStatus(jobId, "FAILED", LocalDateTime.now(), 0, 0, 0, ex.getMessage(), null);
        } finally {
            running.set(false);
            jobLockService.releaseWithOwner(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner);
        }
    }

    private SyncAccumulator syncActivityPages(String jobId, String lockOwner) throws InterruptedException {
        SyncAccumulator accumulator = new SyncAccumulator();
        PageFetchResult firstPage = fetchPage(jobId, 1);
        processPage(firstPage, accumulator, jobId);
        if (accumulator.complete || accumulator.fetchFailed) {
            return accumulator;
        }

        int nextPage = 2;
        int normalizedParallelism = normalizedPageParallelism();
        while (!accumulator.complete && !accumulator.fetchFailed && nextPage <= maxPages) {
            int windowEnd = Math.min(maxPages, nextPage + normalizedParallelism - 1);
            for (PageFetchResult page : fetchPageWindow(jobId, nextPage, windowEnd)) {
                processPage(page, accumulator, jobId);
                if (accumulator.complete || accumulator.fetchFailed) {
                    break;
                }
            }
            nextPage = windowEnd + 1;
            jobLockService.renew(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC, lockOwner, SYNC_LOCK_TTL.toMillis());
        }

        if (!accumulator.complete && !accumulator.fetchFailed) {
            accumulator.failed++;
            accumulator.errorMessage = String.format("达到活动列表最大页数上限 %d，结果不完整", maxPages);
        }
        return accumulator;
    }

    private void processPage(PageFetchResult page, SyncAccumulator accumulator, String jobId) {
        if (page.error != null) {
            accumulator.fetchFailed = true;
            accumulator.failed++;
            accumulator.errorMessage = String.format("第 %d 页拉取失败: %s", page.page, page.error.getMessage());
            return;
        }
        if (page.result == null || page.result.activityList() == null) {
            accumulator.fetchFailed = true;
            accumulator.failed++;
            accumulator.errorMessage = String.format("第 %d 页返回结构为空", page.page);
            return;
        }
        accumulator.pagesFetched++;
        List<DouyinActivityGateway.ActivityItem> items = page.result.activityList();
        if (items.isEmpty()) {
            accumulator.complete = true;
            accumulator.stopReason = "UPSTREAM_EMPTY";
            return;
        }
        accumulator.fetchedRows += items.size();
        // P8.3 修复: 用上游 total 决定同步是否完成 (按用户修正清单)
        // 第 1 页记录 expectedTotal, 累计 fetchedRows >= expectedTotal 立即 complete
        if (page.result.total() > 0L && accumulator.expectedTotal < 0L) {
            accumulator.expectedTotal = page.result.total();
        }
        if (accumulator.expectedTotal > 0L && accumulator.fetchedRows >= accumulator.expectedTotal) {
            // 总数达到上游给出的 total, 立即 complete
            accumulator.complete = true;
            accumulator.stopReason = "UPSTREAM_TOTAL";
        } else if (items.size() < normalizedPageSize()) {
            // 上游未返回 total 或总数未达: 兜底 pageSize
            accumulator.complete = true;
            accumulator.stopReason = "PAGE_SIZE_BOUNDARY";
        }
        for (DouyinActivityGateway.ActivityItem item : items) {
            try {
                activityService.syncFromGatewayItem(item);
                // P8-阻断#4: 活动状态成功落库后, 单独更新 activity_status_synced_at
                if (item != null && item.activityId() > 0L) {
                    try {
                        activityMapper.touchActivityStatusSyncedAt(
                                String.valueOf(item.activityId()), LocalDateTime.now());
                    } catch (Exception ex) {
                        log.warn("ColonelActivityListSync touchActivityStatusSyncedAt failed, jobId={}, activityId={}",
                                jobId, item.activityId(), ex);
                    }
                }
                accumulator.synced++;
            } catch (Exception ex) {
                log.warn("ColonelActivityListSync item failed, jobId={}, activityId={}", jobId,
                        item == null ? null : item.activityId(), ex);
                accumulator.failed++;
            }
        }
        // (P8-阻断#3 已回退, 见上方 pageSize 兜底 - 唯一判断点)
    }

    private List<PageFetchResult> fetchPageWindow(String jobId, int startPage, int endPage)
            throws InterruptedException {
        List<Future<PageFetchResult>> futures = new ArrayList<>();
        for (int page = startPage; page <= endPage; page++) {
            int requestedPage = page;
            futures.add(getPageFetchExecutor().submit(() -> fetchPage(jobId, requestedPage)));
        }
        List<PageFetchResult> results = new ArrayList<>(futures.size());
        try {
            for (int index = 0; index < futures.size(); index++) {
                Future<PageFetchResult> future = futures.get(index);
                try {
                    results.add(future.get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    results.add(PageFetchResult.failure(startPage + index, cause));
                }
            }
            return results;
        } catch (InterruptedException ex) {
            futures.forEach(future -> future.cancel(true));
            throw ex;
        }
    }

    private PageFetchResult fetchPage(String jobId, int page) {
        try {
            DouyinActivityGateway.ActivityListResult result = douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(null, null, null, null,
                            (long) page, (long) normalizedPageSize(), null));
            return PageFetchResult.success(page, result);
        } catch (Exception ex) {
            log.warn("ColonelActivityListSync page fetch failed, jobId={}, page={}", jobId, page, ex);
            return PageFetchResult.failure(page, ex);
        }
    }

    private ExecutorService getPageFetchExecutor() {
        ExecutorService existing = pageFetchExecutor;
        if (existing != null) {
            return existing;
        }
        synchronized (pageFetchExecutorMonitor) {
            if (pageFetchExecutor == null) {
                pageFetchExecutor = Executors.newFixedThreadPool(
                        normalizedPageParallelism(), namedThreadFactory("activity-list-page-"));
            }
            return pageFetchExecutor;
        }
    }

    private int normalizedPageSize() {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private int normalizedPageParallelism() {
        return Math.min(Math.max(pageParallelism, 1), 8);
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdownExecutors() {
        syncExecutor.shutdownNow();
        ExecutorService pageExecutor = pageFetchExecutor;
        if (pageExecutor != null) {
            pageExecutor.shutdownNow();
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

    private String toMetadataJson(int total, int synced, int failed, int pagesFetched, boolean complete) {
        return String.format("{\"total\":%d,\"synced\":%d,\"failed\":%d,\"pagesFetched\":%d,\"complete\":%s}",
                total, synced, failed, pagesFetched, complete);
    }

    private static final class SyncAccumulator {
        private int fetchedRows;
        private int pagesFetched;
        private int synced;
        private int failed;
        private long expectedTotal = -1L;       // 上游 total, -1 = 未知
        private boolean complete;
        private boolean fetchFailed;
        private String errorMessage;
        private String stopReason;               // SUCCESS / MAX_PAGES_REACHED / UPSTREAM_TOTAL

        private boolean isSuccessful() {
            return complete && !fetchFailed && failed == 0;
        }
    }

    private record PageFetchResult(int page, DouyinActivityGateway.ActivityListResult result, Throwable error) {
        private static PageFetchResult success(int page, DouyinActivityGateway.ActivityListResult result) {
            return new PageFetchResult(page, result, null);
        }

        private static PageFetchResult failure(int page, Throwable error) {
            return new PageFetchResult(page, null, error);
        }
    }

    public record SyncTriggerResult(String jobId, String status, String message, boolean reused) {
        public static SyncTriggerResult disabled() {
            return new SyncTriggerResult(null, "DISABLED", "活动列表同步已关闭", false);
        }
        public static SyncTriggerResult running() {
            return new SyncTriggerResult(null, "RUNNING", "上一次活动列表同步尚未完成，请稍后查看", true);
        }
        public static SyncTriggerResult queued(String jobId) {
            return new SyncTriggerResult(jobId, "QUEUED", null, false);
        }
        public static SyncTriggerResult reused(String jobId) {
            return new SyncTriggerResult(jobId, "QUEUED", "复用现有活跃任务", true);
        }
    }

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
