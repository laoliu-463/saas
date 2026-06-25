package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 手动触发的活动商品后台同步服务。
 */
@Slf4j
@Service
public class ProductActivityManualSyncService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final long DEFAULT_MANUAL_PAGE_INTERVAL_MS = 300L;
    private static final int DEFAULT_MANUAL_MAX_PAGES_PER_ACTIVITY = 3000;
    private static final int DEFAULT_MANUAL_MAX_ROWS_PER_ACTIVITY = 50000;
    private static final int MAX_MANUAL_MAX_PAGES_PER_ACTIVITY = 5000;
    private static final int MAX_MANUAL_MAX_ROWS_PER_ACTIVITY = 50000;
    private static final int PROGRESS_UPDATE_PAGE_INTERVAL = 10;
    private static final Duration MANUAL_SYNC_LOCK_TTL = Duration.ofMinutes(120);
    private static final String JOB_TYPE = "activity_product_manual_sync";
    private static final String SCOPE_PREFIX = "ACTIVITY:";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_FAILED = "FAILED";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductService productService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ProductSyncJobLogMapper jobLogMapper;
    private final DistributedJobLockService jobLockService;
    private final TransactionTemplate progressTransactionTemplate;
    private final Executor syncExecutor;
    private final Map<String, ProductSyncJobLog> activeJobsByActivity = new ConcurrentHashMap<>();
    private final Set<String> scheduledJobIds = ConcurrentHashMap.newKeySet();
    private final Set<String> upstreamRunningJobIds = ConcurrentHashMap.newKeySet();
    private long lastManualUpstreamStartMillis = 0L;
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int pageSize;
    @Value("${product.sync.activityProduct.manual-page-interval-ms:300}")
    private long manualPageIntervalMs = DEFAULT_MANUAL_PAGE_INTERVAL_MS;
    @Value("${product.sync.activityProduct.manual-maxPagesPerActivity:3000}")
    private int manualMaxPagesPerActivity = DEFAULT_MANUAL_MAX_PAGES_PER_ACTIVITY;
    @Value("${product.sync.activityProduct.manual-maxRowsPerActivity:50000}")
    private int manualMaxRowsPerActivity = DEFAULT_MANUAL_MAX_ROWS_PER_ACTIVITY;
    @Value("${product.sync.activityProduct.manual-status-partition-parallelism:1}")
    private int manualStatusPartitionParallelism = 1;
    @Value("${product.sync.activityProduct.manual-queue-drain-enabled:true}")
    private boolean manualQueueDrainEnabled = true;
    @Value("${product.sync.activityProduct.manual-queue-drain-batch-size:5}")
    private int manualQueueDrainBatchSize = 5;
    @Value("${product.sync.activityProduct.manual-queue-max-size:100}")
    private int manualQueueMaxSize = 100;
    @Value("${product.sync.activityProduct.manual-upstream-max-concurrency:2}")
    private int manualUpstreamMaxConcurrency = 2;
    @Value("${product.sync.activityProduct.manual-upstream-min-start-interval-ms:300}")
    private long manualUpstreamMinStartIntervalMs = 300L;

    ProductActivityManualSyncService(
            ProductService productService,
            ColonelsettlementActivityService colonelActivityService,
            ColonelsettlementActivityMapper activityMapper,
            ProductSyncJobLogMapper jobLogMapper,
            @Qualifier("applicationTaskExecutor") Executor syncExecutor) {
        this(productService, colonelActivityService, activityMapper, jobLogMapper, null, null, syncExecutor);
    }

    @Autowired
    public ProductActivityManualSyncService(
            ProductService productService,
            ColonelsettlementActivityService colonelActivityService,
            ColonelsettlementActivityMapper activityMapper,
            ProductSyncJobLogMapper jobLogMapper,
            DistributedJobLockService jobLockService,
            PlatformTransactionManager transactionManager,
            @Qualifier("applicationTaskExecutor") Executor syncExecutor) {
        this.productService = productService;
        this.colonelActivityService = colonelActivityService;
        this.activityMapper = activityMapper;
        this.jobLogMapper = jobLogMapper;
        this.jobLockService = jobLockService;
        this.progressTransactionTemplate = createRequiresNewTemplate(transactionManager);
        this.syncExecutor = syncExecutor;
    }

    public SyncTriggerResult trigger(String activityId, String appId) {
        return trigger(activityId, appId, null);
    }

    public SyncTriggerResult trigger(String activityId, String appId, UUID requestedBy) {
        String normalizedActivityId = activityId == null ? "" : activityId.trim();
        if (!StringUtils.hasText(normalizedActivityId)) {
            return new SyncTriggerResult("", null, "INVALID");
        }
        String jobId = "activity-product-sync-" + UUID.randomUUID();
        ProductSyncJobLog jobLog;
        synchronized (activeJobsByActivity) {
            ProductSyncJobLog activeJob = activeJobsByActivity.get(normalizedActivityId);
            if (activeJob == null || !isActiveStatus(activeJob.getStatus())) {
                activeJob = jobLogMapper.selectLatestActiveByJobTypeAndScope(
                        JOB_TYPE,
                        SCOPE_PREFIX + normalizedActivityId);
            }
            if (activeJob != null && isActiveStatus(activeJob.getStatus())) {
                activeJobsByActivity.put(normalizedActivityId, activeJob);
                String triggerStatus = activeJob.getStatus();
                scheduleQueuedJob(activeJob, appId);
                return new SyncTriggerResult(
                        normalizedActivityId,
                        activeJob.getJobId(),
                        triggerStatus);
            }
            if (jobLogMapper.countQueuedJobs(JOB_TYPE) >= normalizedManualQueueMaxSize()) {
                return new SyncTriggerResult(
                        normalizedActivityId,
                        null,
                        "QUEUE_FULL",
                        "活动商品同步队列已满，请稍后重试",
                        null,
                        null,
                        0L);
            }
            jobLog = createQueuedJob(jobId, normalizedActivityId, appId, requestedBy);
            if (!jobId.equals(jobLog.getJobId())) {
                activeJobsByActivity.put(normalizedActivityId, jobLog);
                String triggerStatus = jobLog.getStatus();
                scheduleQueuedJob(jobLog, appId);
                return new SyncTriggerResult(
                        normalizedActivityId,
                        jobLog.getJobId(),
                        triggerStatus);
            }
            activeJobsByActivity.put(normalizedActivityId, jobLog);
        }
        scheduleQueuedJob(jobLog, appId);
        return new SyncTriggerResult(normalizedActivityId, jobId, STATUS_QUEUED);
    }

    @Scheduled(fixedDelayString = "${product.sync.activityProduct.manual-queue-drain-delay-ms:5000}")
    public void drainQueuedManualSyncJobs() {
        if (!manualQueueDrainEnabled) {
            return;
        }
        List<ProductSyncJobLog> queuedJobs = jobLogMapper.selectQueuedJobs(
                JOB_TYPE,
                normalizedManualQueueDrainBatchSize());
        for (ProductSyncJobLog queuedJob : queuedJobs) {
            String activityId = activityIdFromScope(queuedJob.getScope());
            if (StringUtils.hasText(activityId)) {
                activeJobsByActivity.put(activityId, queuedJob);
                scheduleQueuedJob(queuedJob, appIdFromRequestParams(queuedJob.getRequestParamsJson()));
            }
        }
    }

    public SyncJobStatus getJobStatus(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            throw BusinessException.param("jobId 不能为空");
        }
        ProductSyncJobLog jobLog = jobLogMapper.selectLatestByJobId(jobId.trim());
        if (jobLog == null || !JOB_TYPE.equals(jobLog.getJobType())) {
            throw BusinessException.notFound("未找到对应的活动商品同步任务");
        }
        return new SyncJobStatus(
                jobLog.getJobId(),
                activityIdFromScope(jobLog.getScope()),
                jobLog.getStatus(),
                valueOrZero(jobLog.getApiFetchedRows()),
                valueOrZero(jobLog.getApiDistinctProductIds()),
                valueOrZero(jobLog.getInserted()),
                valueOrZero(jobLog.getUpdated()),
                valueOrZero(jobLog.getSkipped()),
                valueOrZero(jobLog.getFailed()),
                jobLog.getStartedAt() == null ? null : jobLog.getStartedAt().toString(),
                jobLog.getFinishedAt() == null ? null : jobLog.getFinishedAt().toString(),
                jobLog.getErrorMessage());
    }

    private ProductSyncJobLog createQueuedJob(String jobId, String activityId, String appId, UUID requestedBy) {
        ProductSyncJobLog log = new ProductSyncJobLog();
        LocalDateTime now = LocalDateTime.now();
        log.setId(UUID.randomUUID());
        log.setJobId(jobId);
        log.setJobType(JOB_TYPE);
        log.setScope(SCOPE_PREFIX + activityId);
        log.setDryRun(false);
        log.setStatus(STATUS_QUEUED);
        log.setRequestedBy(requestedBy);
        Map<String, Object> requestParams = new LinkedHashMap<>();
        requestParams.put("activityId", activityId);
        requestParams.put("pageSize", normalizedPageSize());
        requestParams.put("maxPagesPerActivity", normalizedManualMaxPagesPerActivity());
        requestParams.put("maxRowsPerActivity", normalizedManualMaxRowsPerActivity());
        requestParams.put("statusPartitionParallelism", normalizedManualStatusPartitionParallelism());
        requestParams.put("queuedAt", now.toString());
        requestParams.put("lastQueueAttemptAt", now.toString());
        if (StringUtils.hasText(appId)) {
            requestParams.put("appId", appId);
        }
        log.setRequestParamsJson(toJson(requestParams));
        log.setStartedAt(now);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        try {
            jobLogMapper.insert(log);
            return log;
        } catch (DuplicateKeyException ex) {
            ProductSyncJobLog activeJob = jobLogMapper.selectLatestActiveByJobTypeAndScope(
                    JOB_TYPE,
                    SCOPE_PREFIX + activityId);
            if (activeJob != null && isActiveStatus(activeJob.getStatus())) {
                return activeJob;
            }
            throw ex;
        }
    }

    private void scheduleQueuedJob(ProductSyncJobLog jobLog, String appId) {
        if (jobLog == null || !STATUS_QUEUED.equals(jobLog.getStatus()) || !StringUtils.hasText(jobLog.getJobId())) {
            return;
        }
        if (!scheduledJobIds.add(jobLog.getJobId())) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    runQueuedSync(jobLog, appId);
                } catch (Exception ex) {
                    finishFailedJob(jobLog, ex);
                    activeJobsByActivity.remove(activityIdFromScope(jobLog.getScope()), jobLog);
                    log.warn("ProductActivityManualSync queued worker failed, jobId={}", jobLog.getJobId(), ex);
                } finally {
                    scheduledJobIds.remove(jobLog.getJobId());
                }
            }, syncExecutor);
        } catch (RuntimeException ex) {
            scheduledJobIds.remove(jobLog.getJobId());
            finishFailedJob(jobLog, ex);
            activeJobsByActivity.remove(activityIdFromScope(jobLog.getScope()), jobLog);
            throw ex;
        }
    }

    private void runQueuedSync(ProductSyncJobLog jobLog, String appId) {
        String activityId = activityIdFromScope(jobLog.getScope());
        String activityLockKey = JobLockKeys.productBackfillActivityLock(activityId);
        String lockOwner = manualSyncLockOwner(jobLog.getJobId(), activityId);
        ManualSyncLockState lockState = tryAcquireManualSyncLocks(activityId, activityLockKey, lockOwner);
        if (!lockState.acquired()) {
            updateQueuedLockState(jobLog, lockState);
            log.info(
                    "ProductActivityManualSync queued, activityId={}, jobId={}, lockKey={}, lockOwner={}, lockTtlSeconds={}",
                    activityId,
                    jobLog.getJobId(),
                    lockState.lockKey(),
                    lockState.lockOwner(),
                    lockState.lockTtlSeconds());
            return;
        }
        boolean enteredUpstreamSlot = false;
        try {
            if (!tryEnterManualUpstreamSlot(jobLog.getJobId())) {
                updateQueuedLimiterState(jobLog);
                releaseManualSyncLocks(
                        activityId,
                        activityLockKey,
                        lockState.acquiredActivityLock(),
                        lockState.acquiredGlobalLock(),
                        lockOwner);
                return;
            }
            enteredUpstreamSlot = true;
        if (!markJobRunning(jobLog)) {
            releaseManualSyncLocks(
                    activityId,
                    activityLockKey,
                    lockState.acquiredActivityLock(),
                    lockState.acquiredGlobalLock(),
                    lockOwner);
            activeJobsByActivity.remove(activityId, jobLog);
            return;
        }
        runSync(
                activityId,
                appId,
                jobLog,
                activityLockKey,
                lockOwner,
                lockState.acquiredActivityLock(),
                lockState.acquiredGlobalLock());
        } finally {
            if (enteredUpstreamSlot) {
                releaseManualUpstreamSlot(jobLog.getJobId());
            }
        }
    }

    private boolean markJobRunning(ProductSyncJobLog jobLog) {
        LocalDateTime now = LocalDateTime.now();
        jobLog.setStatus(STATUS_RUNNING);
        jobLog.setStartedAt(now);
        jobLog.setErrorMessage(null);
        jobLog.setRequestParamsJson(appendMeta(jobLog.getRequestParamsJson(), Map.of(
                "startedAt", now.toString(),
                "lastProgressAt", now.toString())));
        jobLog.setUpdateTime(now);
        return jobLogMapper.markQueuedJobRunning(jobLog.getId(), now, jobLog.getRequestParamsJson()) > 0;
    }

    private void updateQueuedLockState(ProductSyncJobLog jobLog, ManualSyncLockState lockState) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("lastQueueAttemptAt", now.toString());
        meta.put("queuedReason", lockState.message());
        meta.put("lockKey", lockState.lockKey());
        if (StringUtils.hasText(lockState.lockOwner())) {
            meta.put("lockOwner", lockState.lockOwner());
        }
        meta.put("lockTtlSeconds", lockState.lockTtlSeconds());
        jobLog.setStatus(STATUS_QUEUED);
        jobLog.setErrorMessage(lockState.message());
        jobLog.setRequestParamsJson(appendMeta(jobLog.getRequestParamsJson(), meta));
        jobLog.setUpdateTime(now);
        jobLogMapper.updateById(jobLog);
    }

    private void updateQueuedLimiterState(ProductSyncJobLog jobLog) {
        LocalDateTime now = LocalDateTime.now();
        String message = "活动商品同步上游并发槽已满，任务保持排队";
        jobLog.setStatus(STATUS_QUEUED);
        jobLog.setErrorMessage(message);
        jobLog.setRequestParamsJson(appendMeta(jobLog.getRequestParamsJson(), Map.of(
                "lastQueueAttemptAt", now.toString(),
                "queuedReason", message,
                "upstreamRunningJobs", upstreamRunningJobIds.size(),
                "upstreamMaxConcurrency", normalizedManualUpstreamMaxConcurrency())));
        jobLog.setUpdateTime(now);
        jobLogMapper.updateById(jobLog);
    }

    private void runSync(
            String activityId,
            String appId,
            ProductSyncJobLog jobLog,
            String activityLockKey,
            String lockOwner,
            boolean acquiredActivityLock,
            boolean acquiredGlobalLock) {
        try {
            colonelActivityService.syncActivitySummaryFromUpstream(activityId, appId);
            ProductService.ActivityProductRefreshResult result =
                    productService.refreshActivitySnapshotsByStatusPartitions(
                            buildQueryRequest(activityId, appId),
                            normalizedManualMaxPagesPerActivity(),
                            normalizedManualMaxRowsPerActivity(),
                            normalizedManualPageIntervalMs(),
                            normalizedManualStatusPartitionParallelism(),
                            progress -> updateRunningProgress(jobLog, progress));
            if (result.complete()) {
                activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
            }
            finishJob(jobLog, result, result.complete() ? STATUS_SUCCESS : STATUS_PARTIAL, null);
            log.info(
                    "ProductActivityManualSync completed, activityId={}, syncedProductCount={}, libraryEntryCount={}, createdCount={}, updatedCount={}, skippedCount={}, pagesFetched={}, fetchedRows={}, stoppedReason={}, stillHasNextWhenStopped={}, complete={}",
                    activityId,
                    result.syncedProductCount(),
                    result.libraryEntryCount(),
                    result.createdCount(),
                    result.updatedCount(),
                    result.skippedCount(),
                    result.pagesFetched(),
                    result.fetchedRows(),
                    result.stoppedReason(),
                    result.stillHasNextWhenStopped(),
                    result.complete());
        } catch (Exception ex) {
            finishFailedJob(jobLog, ex);
            log.warn("ProductActivityManualSync failed, activityId={}", activityId, ex);
        } finally {
            releaseManualSyncLocks(activityId, activityLockKey, acquiredActivityLock, acquiredGlobalLock, lockOwner);
            activeJobsByActivity.remove(activityId, jobLog);
        }
    }

    private ManualSyncLockState tryAcquireManualSyncLocks(String activityId, String activityLockKey, String lockOwner) {
        if (jobLockService == null) {
            return ManualSyncLockState.acquired(activityLockKey, false, false);
        }
        String globalLockOwner = safeCurrentLockOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
        if (StringUtils.hasText(globalLockOwner)) {
            return ManualSyncLockState.locked(
                    activityLockKey,
                    JobLockKeys.PRODUCT_BACKFILL_GLOBAL,
                    globalLockOwner,
                    safeCurrentLockTtlSeconds(JobLockKeys.PRODUCT_BACKFILL_GLOBAL),
                    "商品回补全局锁被占用，活动商品同步已排队等待执行");
        }
        boolean acquiredActivityLock = false;
        try {
            acquiredActivityLock = jobLockService.tryAcquire(activityLockKey, MANUAL_SYNC_LOCK_TTL, lockOwner);
            if (!acquiredActivityLock) {
                return ManualSyncLockState.locked(
                        activityLockKey,
                        activityLockKey,
                        safeCurrentLockOwner(activityLockKey),
                        safeCurrentLockTtlSeconds(activityLockKey),
                        "当前活动商品同步锁被占用，请等待当前活动任务完成后重试");
            }
            return ManualSyncLockState.acquired(activityLockKey, true, false);
        } finally {
            if (!acquiredActivityLock) {
                releaseManualSyncLocks(activityId, activityLockKey, false, false, lockOwner);
            }
        }
    }

    private synchronized boolean tryEnterManualUpstreamSlot(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return false;
        }
        int maxConcurrency = normalizedManualUpstreamMaxConcurrency();
        if (upstreamRunningJobIds.size() >= maxConcurrency) {
            return false;
        }
        long now = System.currentTimeMillis();
        long minIntervalMs = normalizedManualUpstreamMinStartIntervalMs();
        if (lastManualUpstreamStartMillis > 0L && now - lastManualUpstreamStartMillis < minIntervalMs) {
            return false;
        }
        upstreamRunningJobIds.add(jobId);
        lastManualUpstreamStartMillis = now;
        return true;
    }

    private synchronized void releaseManualUpstreamSlot(String jobId) {
        if (StringUtils.hasText(jobId)) {
            upstreamRunningJobIds.remove(jobId);
        }
    }

    private void releaseManualSyncLocks(
            String activityId,
            String activityLockKey,
            boolean acquiredActivityLock,
            boolean acquiredGlobalLock,
            String lockOwner) {
        if (jobLockService == null) {
            return;
        }
        try {
            if (acquiredActivityLock) {
                jobLockService.releaseWithOwner(activityLockKey, lockOwner);
            }
            if (acquiredGlobalLock) {
                jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, lockOwner);
            }
        } catch (Exception ex) {
            log.warn("ProductActivityManualSync lock release failed, activityId={}, message={}",
                    activityId,
                    ex.getMessage());
        }
    }

    private String safeCurrentLockOwner(String lockKey) {
        try {
            return jobLockService == null ? null : jobLockService.currentLockValue(lockKey);
        } catch (Exception ex) {
            log.warn("ProductActivityManualSync lock owner query failed, lockKey={}, message={}",
                    lockKey,
                    ex.getMessage());
            return null;
        }
    }

    private long safeCurrentLockTtlSeconds(String lockKey) {
        try {
            return jobLockService == null ? 0L : jobLockService.currentLockTtlSeconds(lockKey);
        } catch (Exception ex) {
            log.warn("ProductActivityManualSync lock ttl query failed, lockKey={}, message={}",
                    lockKey,
                    ex.getMessage());
            return 0L;
        }
    }

    private String manualSyncLockOwner(String jobId, String activityId) {
        return "manual:" + jobId + ":activity:" + activityId;
    }

    private void updateRunningProgress(ProductSyncJobLog jobLog, ProductService.ActivityProductRefreshProgress progress) {
        if (jobLog == null || progress == null) {
            return;
        }
        int pagesFetched = progress.pagesFetched();
        if (pagesFetched <= 0 || pagesFetched % PROGRESS_UPDATE_PAGE_INTERVAL != 0) {
            return;
        }
        synchronized (jobLog) {
            LocalDateTime now = LocalDateTime.now();
            jobLog.setApiFetchedRows((long) pagesFetched * normalizedPageSize());
            jobLog.setActivitiesScanned(1);
            jobLog.setRequestParamsJson(appendMeta(jobLog.getRequestParamsJson(), Map.of(
                    "lastProgressAt", now.toString(),
                    "pagesFetched", pagesFetched,
                    "progressApproximate", true)));
            jobLog.setUpdateTime(now);
            try {
                updateJobLogProgress(jobLog);
            } catch (Exception ex) {
                log.warn("ProductActivityManualSync progress update failed, jobId={}, pagesFetched={}, message={}",
                        jobLog.getJobId(),
                        pagesFetched,
                        ex.getMessage());
            }
        }
    }

    private void updateJobLogProgress(ProductSyncJobLog jobLog) {
        if (progressTransactionTemplate == null) {
            jobLogMapper.updateById(jobLog);
            return;
        }
        progressTransactionTemplate.executeWithoutResult(ignored -> jobLogMapper.updateById(jobLog));
    }

    private void finishJob(
            ProductSyncJobLog log,
            ProductService.ActivityProductRefreshResult result,
            String status,
            String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        log.setStatus(status);
        log.setFinishedAt(now);
        log.setActivitiesScanned(1);
        log.setActivitiesSuccess(STATUS_SUCCESS.equals(status) ? 1 : 0);
        log.setActivitiesIncomplete(STATUS_PARTIAL.equals(status) ? 1 : 0);
        log.setActivitiesFailed(STATUS_FAILED.equals(status) ? 1 : 0);
        log.setApiFetchedRows((long) result.fetchedRows());
        log.setApiDistinctProductIds((long) result.distinctProductIds());
        log.setInserted(result.createdCount());
        log.setUpdated(result.updatedCount());
        log.setSkipped(result.skippedCount());
        log.setFailed(STATUS_FAILED.equals(status) ? 1 : 0);
        String stopReason = StringUtils.hasText(result.stoppedReason()) ? result.stoppedReason() : "UNKNOWN";
        log.setStopReasonStatsJson(toJson(Map.of(stopReason, 1L)));
        log.setErrorMessage(errorMessage);
        log.setRequestParamsJson(appendMeta(log.getRequestParamsJson(), Map.of(
                "lastProgressAt", now.toString(),
                "libraryEntryCount", result.libraryEntryCount(),
                "syncedProductCount", result.syncedProductCount(),
                "pagesFetched", result.pagesFetched(),
                "stillHasNextWhenStopped", result.stillHasNextWhenStopped(),
                "progressApproximate", false)));
        log.setUpdateTime(now);
        jobLogMapper.updateById(log);
    }

    private void finishFailedJob(ProductSyncJobLog log, Exception ex) {
        LocalDateTime now = LocalDateTime.now();
        log.setStatus(STATUS_FAILED);
        log.setFinishedAt(now);
        log.setActivitiesScanned(1);
        log.setActivitiesSuccess(0);
        log.setActivitiesIncomplete(0);
        log.setActivitiesFailed(1);
        log.setApiFetchedRows(valueOrZero(log.getApiFetchedRows()));
        log.setApiDistinctProductIds(valueOrZero(log.getApiDistinctProductIds()));
        log.setInserted(valueOrZero(log.getInserted()));
        log.setUpdated(valueOrZero(log.getUpdated()));
        log.setSkipped(valueOrZero(log.getSkipped()));
        log.setFailed(1);
        log.setStopReasonStatsJson(toJson(Map.of("EXCEPTION", 1L)));
        log.setErrorMessage(ex == null ? "unknown error" : ex.getMessage());
        log.setRequestParamsJson(appendMeta(log.getRequestParamsJson(), Map.of(
                "lastProgressAt", now.toString())));
        log.setUpdateTime(now);
        jobLogMapper.updateById(log);
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId, String appId) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                appId,
                activityId,
                4L,
                1L,
                normalizedPageSize(),
                null,
                null,
                null,
                null,
                1L,
                null,
                null);
    }

    private int normalizedPageSize() {
        return Math.min(Math.max(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize, 1), 20);
    }

    private long normalizedManualPageIntervalMs() {
        return Math.min(1000L, Math.max(300L, manualPageIntervalMs));
    }

    private int normalizedManualStatusPartitionParallelism() {
        return Math.min(Math.max(manualStatusPartitionParallelism, 1), 6);
    }

    private int normalizedManualQueueDrainBatchSize() {
        return Math.min(Math.max(manualQueueDrainBatchSize, 1), 20);
    }

    private int normalizedManualQueueMaxSize() {
        return Math.min(Math.max(manualQueueMaxSize, 1), 1000);
    }

    private int normalizedManualUpstreamMaxConcurrency() {
        return Math.min(Math.max(manualUpstreamMaxConcurrency, 1), 10);
    }

    private long normalizedManualUpstreamMinStartIntervalMs() {
        return Math.min(Math.max(manualUpstreamMinStartIntervalMs, 0L), 10_000L);
    }

    private static TransactionTemplate createRequiresNewTemplate(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            return null;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private int normalizedManualMaxPagesPerActivity() {
        int configured = manualMaxPagesPerActivity <= 0
                ? DEFAULT_MANUAL_MAX_PAGES_PER_ACTIVITY
                : manualMaxPagesPerActivity;
        return Math.min(Math.max(configured, 1), MAX_MANUAL_MAX_PAGES_PER_ACTIVITY);
    }

    private int normalizedManualMaxRowsPerActivity() {
        int configured = manualMaxRowsPerActivity <= 0
                ? DEFAULT_MANUAL_MAX_ROWS_PER_ACTIVITY
                : manualMaxRowsPerActivity;
        return Math.min(Math.max(configured, 1), MAX_MANUAL_MAX_ROWS_PER_ACTIVITY);
    }

    private String activityIdFromScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "";
        }
        return scope.startsWith(SCOPE_PREFIX) ? scope.substring(SCOPE_PREFIX.length()) : scope;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String appendMeta(String original, Map<String, Object> updates) {
        if (!StringUtils.hasText(original)) {
            original = "{}";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(original, LinkedHashMap.class);
            map.putAll(updates);
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return original;
        }
    }

    private String appIdFromRequestParams(String requestParamsJson) {
        if (!StringUtils.hasText(requestParamsJson)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(requestParamsJson, LinkedHashMap.class);
            Object appId = map.get("appId");
            return appId == null ? null : String.valueOf(appId);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private boolean isActiveStatus(String status) {
        return STATUS_QUEUED.equals(status) || STATUS_RUNNING.equals(status);
    }

    private String toJson(Map<String, ?> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record ManualSyncLockState(
            boolean acquired,
            String activityLockKey,
            boolean acquiredActivityLock,
            boolean acquiredGlobalLock,
            String lockKey,
            String lockOwner,
            long lockTtlSeconds,
            String message) {
        private static ManualSyncLockState acquired(
                String activityLockKey,
                boolean acquiredActivityLock,
                boolean acquiredGlobalLock) {
            return new ManualSyncLockState(
                    true,
                    activityLockKey,
                    acquiredActivityLock,
                    acquiredGlobalLock,
                    null,
                    null,
                    0L,
                    null);
        }

        private static ManualSyncLockState locked(
                String activityLockKey,
                String lockKey,
                String lockOwner,
                long lockTtlSeconds,
                String message) {
            return new ManualSyncLockState(
                    false,
                    activityLockKey,
                    false,
                    false,
                    lockKey,
                    lockOwner,
                    lockTtlSeconds,
                    message);
        }
    }

    public record SyncTriggerResult(
            String activityId,
            String jobId,
            String syncStatus,
            String message,
            String lockKey,
            String lockOwner,
            long lockTtlSeconds) {
        public SyncTriggerResult(String activityId, String jobId, String syncStatus) {
            this(activityId, jobId, syncStatus, null, null, null, 0L);
        }

        public SyncTriggerResult(String activityId, String syncStatus) {
            this(activityId, null, syncStatus);
        }
    }

    public record SyncJobStatus(
            String jobId,
            String activityId,
            String syncStatus,
            long fetchedRows,
            long distinctProductIds,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int failedCount,
            String startedAt,
            String finishedAt,
            String errorMessage) {
    }
}
