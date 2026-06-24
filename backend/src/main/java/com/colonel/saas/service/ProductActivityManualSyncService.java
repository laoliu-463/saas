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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductService productService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ProductSyncJobLogMapper jobLogMapper;
    private final DistributedJobLockService jobLockService;
    private final TransactionTemplate progressTransactionTemplate;
    private final Executor syncExecutor;
    private final Map<String, String> runningJobIdsByActivity = new ConcurrentHashMap<>();
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int pageSize;
    @Value("${product.sync.activityProduct.manual-page-interval-ms:300}")
    private long manualPageIntervalMs = DEFAULT_MANUAL_PAGE_INTERVAL_MS;
    @Value("${product.sync.activityProduct.manual-maxPagesPerActivity:3000}")
    private int manualMaxPagesPerActivity = DEFAULT_MANUAL_MAX_PAGES_PER_ACTIVITY;
    @Value("${product.sync.activityProduct.manual-maxRowsPerActivity:50000}")
    private int manualMaxRowsPerActivity = DEFAULT_MANUAL_MAX_ROWS_PER_ACTIVITY;

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
        synchronized (runningJobIdsByActivity) {
            String existingJobId = runningJobIdsByActivity.get(normalizedActivityId);
            if (existingJobId != null) {
                return new SyncTriggerResult(normalizedActivityId, existingJobId, "RUNNING");
            }
            jobLog = startJob(jobId, normalizedActivityId, requestedBy);
            runningJobIdsByActivity.put(normalizedActivityId, jobId);
        }
        try {
            ProductSyncJobLog asyncJobLog = jobLog;
            CompletableFuture.runAsync(() -> runSync(normalizedActivityId, appId, asyncJobLog), syncExecutor);
        } catch (RuntimeException ex) {
            finishFailedJob(jobLog, ex);
            runningJobIdsByActivity.remove(normalizedActivityId, jobId);
            throw ex;
        }
        return new SyncTriggerResult(normalizedActivityId, jobId, "ACCEPTED");
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

    private ProductSyncJobLog startJob(String jobId, String activityId, UUID requestedBy) {
        ProductSyncJobLog log = new ProductSyncJobLog();
        LocalDateTime now = LocalDateTime.now();
        log.setId(UUID.randomUUID());
        log.setJobId(jobId);
        log.setJobType(JOB_TYPE);
        log.setScope(SCOPE_PREFIX + activityId);
        log.setDryRun(false);
        log.setStatus("RUNNING");
        log.setRequestedBy(requestedBy);
        log.setRequestParamsJson(toJson(Map.of(
                "activityId", activityId,
                "pageSize", normalizedPageSize(),
                "maxPagesPerActivity", normalizedManualMaxPagesPerActivity(),
                "maxRowsPerActivity", normalizedManualMaxRowsPerActivity(),
                "lastProgressAt", now.toString())));
        log.setStartedAt(now);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        jobLogMapper.insert(log);
        return log;
    }

    private void runSync(String activityId, String appId, ProductSyncJobLog jobLog) {
        String activityLockKey = JobLockKeys.productBackfillActivityLock(activityId);
        boolean acquiredGlobalLock = false;
        boolean acquiredActivityLock = false;
        try {
            if (jobLockService != null) {
                acquiredGlobalLock = jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, MANUAL_SYNC_LOCK_TTL);
                if (!acquiredGlobalLock) {
                    throw new IllegalStateException("商品同步全局锁被占用，请等待当前商品同步或回补任务完成后重试");
                }
                acquiredActivityLock = jobLockService.tryAcquire(activityLockKey, MANUAL_SYNC_LOCK_TTL);
                if (!acquiredActivityLock) {
                    throw new IllegalStateException("当前活动商品同步锁被占用，请等待当前活动任务完成后重试");
                }
            }
            colonelActivityService.syncActivitySummaryFromUpstream(activityId, appId);
            ProductService.ActivityProductRefreshResult result =
                    productService.refreshActivitySnapshots(
                            buildQueryRequest(activityId, appId),
                            normalizedManualMaxPagesPerActivity(),
                            normalizedManualMaxRowsPerActivity(),
                            normalizedManualPageIntervalMs(),
                            progress -> updateRunningProgress(jobLog, progress));
            if (result.complete()) {
                activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
            }
            finishJob(jobLog, result, result.complete() ? "SUCCESS" : "PARTIAL", null);
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
            releaseManualSyncLocks(activityId, activityLockKey, acquiredActivityLock, acquiredGlobalLock);
            runningJobIdsByActivity.remove(activityId, jobLog.getJobId());
        }
    }

    private void releaseManualSyncLocks(
            String activityId,
            String activityLockKey,
            boolean acquiredActivityLock,
            boolean acquiredGlobalLock) {
        if (jobLockService == null) {
            return;
        }
        try {
            if (acquiredActivityLock) {
                jobLockService.release(activityLockKey);
            }
            if (acquiredGlobalLock) {
                jobLockService.release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
            }
        } catch (Exception ex) {
            log.warn("ProductActivityManualSync lock release failed, activityId={}, message={}",
                    activityId,
                    ex.getMessage());
        }
    }

    private void updateRunningProgress(ProductSyncJobLog jobLog, ProductService.ActivityProductRefreshProgress progress) {
        if (jobLog == null || progress == null) {
            return;
        }
        int pagesFetched = progress.pagesFetched();
        if (pagesFetched <= 0 || pagesFetched % PROGRESS_UPDATE_PAGE_INTERVAL != 0) {
            return;
        }
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
        log.setActivitiesSuccess("SUCCESS".equals(status) ? 1 : 0);
        log.setActivitiesIncomplete("PARTIAL".equals(status) ? 1 : 0);
        log.setActivitiesFailed("FAILED".equals(status) ? 1 : 0);
        log.setApiFetchedRows((long) result.fetchedRows());
        log.setApiDistinctProductIds((long) result.distinctProductIds());
        log.setInserted(result.createdCount());
        log.setUpdated(result.updatedCount());
        log.setSkipped(result.skippedCount());
        log.setFailed("FAILED".equals(status) ? 1 : 0);
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
        log.setStatus("FAILED");
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

    private String toJson(Map<String, ?> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public record SyncTriggerResult(String activityId, String jobId, String syncStatus) {
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
