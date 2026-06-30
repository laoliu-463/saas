package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.product.application.ProductBackfillJobMetadata;
import com.colonel.saas.domain.product.policy.ProductBackfillFailurePolicy;
import com.colonel.saas.domain.product.policy.ProductBackfillJobPolicy;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductActivitySyncState;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductActivitySyncStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

/**
 * 活动商品全量回补服务。
 *
 * <p>Phase 4-1.5 deadlock 修复要点：</p>
 * <ul>
 *   <li>每次写库前先获取 {@link JobLockKeys#PRODUCT_BACKFILL_GLOBAL} 与
 *       {@code PRODUCT_BACKFILL_ACTIVITY:{activityId}} 两把 Redis 锁；与 {@code ProductActivitySyncJob}
 *       和 {@code ProductDisplayRuleJob} 的写库动作互斥。</li>
 *   <li>backfill 默认只补事实层，{@code displayRefreshMode=DEFERRED} 表示写完事实层后单独再排
 *       {@code PRODUCT_DISPLAY_REFRESH} 锁触发展示规则刷新，避免与 backfill 写事实层共用同一大事务。</li>
 *   <li>每个 activity 的 page 列表在写入前按 {@code product_id} 排序；每个 page 单独走独立子事务，
 *       子事务内单 batch（{@code writeBatchSize} 控制）独立提交。</li>
 *   <li>子事务遇到 PostgreSQL {@code 40P01 deadlock_detected} 或 {@code 55P03 lock_not_available}
 *       时按 {@code deadlockRetryMax} 配置做指数退避重试，避免一次死锁直接放弃。</li>
 *   <li>job log 写入、updateById 全部包在 {@code try/finally}，确保任何异常路径下都能
 *       写入 {@code finished_at}；同时新增 stale RUNNING 清理任务
 *       （见 {@code StaleProductSyncJobReconcileJob}）。</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductActivityBackfillService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_MAX_ACTIVITIES = 50;
    private static final int DEFAULT_MAX_PAGES = 1000;
    private static final int DEFAULT_MAX_ROWS = 50_000;
    private static final int MAX_ACTIVITIES = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 子事务默认 batch 大小。 */
    private static final int DEFAULT_WRITE_BATCH_SIZE = 100;
    /** 死锁重试默认次数。 */
    private static final int DEFAULT_DEADLOCK_RETRY_MAX = 3;
    /** 写锁 TTL（与最坏单 activity 写满时间匹配）。 */
    private static final Duration BACKFILL_LOCK_TTL = Duration.ofMinutes(30);
    private static final String STOP_REASON_FAILED_LOCKED = ProductBackfillJobPolicy.STOP_REASON_FAILED_LOCKED;
    private static final String STOP_REASON_DEADLOCK_RETRY_EXHAUSTED =
            ProductBackfillJobPolicy.STOP_REASON_DEADLOCK_RETRY_EXHAUSTED;
    private static final String STOP_REASON_UPSTREAM_API_ERROR =
            ProductBackfillJobPolicy.STOP_REASON_UPSTREAM_API_ERROR;
    private static final String STOP_REASON_DB_ERROR = ProductBackfillJobPolicy.STOP_REASON_DB_ERROR;
    private static final String STOP_REASON_LOCK_ERROR = ProductBackfillJobPolicy.STOP_REASON_LOCK_ERROR;
    private static final String STOP_REASON_TIMEOUT_ERROR = ProductBackfillJobPolicy.STOP_REASON_TIMEOUT_ERROR;
    private static final String STOP_REASON_UNKNOWN_ERROR = ProductBackfillJobPolicy.STOP_REASON_UNKNOWN_ERROR;

    private final ProductSyncDryRunProbeService dryRunProbeService;
    private final ProductService productService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ProductSnapshotMapper snapshotMapper;
    private final ProductSyncJobLogMapper jobLogMapper;
    private final ProductActivitySyncStateMapper syncStateMapper;
    private final DistributedJobLockService jobLockService;
    private final ProductDisplayRuleService productDisplayRuleService;
    private final DouyinProductGateway douyinProductGateway;
    private final Executor backfillExecutor;
    private final TransactionTemplate batchTransactionTemplate;
    private final ProductBackfillJobMetadata backfillJobMetadata = new ProductBackfillJobMetadata();
    private final ProductBackfillJobPolicy backfillJobPolicy = new ProductBackfillJobPolicy();
    private final ProductBackfillFailurePolicy backfillFailurePolicy = new ProductBackfillFailurePolicy();
    private final ConcurrentMap<String, String> runningAsyncJobIds = new ConcurrentHashMap<>();
    @Value("${product.sync.activityProduct.fullBackfillEnabled:true}")
    private boolean fullBackfillEnabled = true;
    @Value("${product.sync.backfill.writeBatchSize:100}")
    private int writeBatchSize = DEFAULT_WRITE_BATCH_SIZE;
    @Value("${product.sync.backfill.deadlockRetryMax:3}")
    private int deadlockRetryMax = DEFAULT_DEADLOCK_RETRY_MAX;
    @Value("${product.sync.backfill.lockWaitSeconds:10}")
    private int lockWaitSeconds = 10;
    @Value("${product.sync.backfill.displayRefreshMode:DEFERRED}")
    private String defaultDisplayRefreshMode = "DEFERRED";
    @Value("${product.sync.backfill.skipDisplayRefreshForExpiredActivity:true}")
    private boolean skipDisplayRefreshForExpiredActivity = true;
    @Value("${product.sync.backfill.apiSleepMs:100}")
    private long backfillApiSleepMs = 100L;

    @Autowired
    public ProductActivityBackfillService(
            ProductSyncDryRunProbeService dryRunProbeService,
            ProductService productService,
            ColonelsettlementActivityMapper activityMapper,
            ProductSnapshotMapper snapshotMapper,
            ProductSyncJobLogMapper jobLogMapper,
            ProductActivitySyncStateMapper syncStateMapper,
            DistributedJobLockService jobLockService,
            ProductDisplayRuleService productDisplayRuleService,
            DouyinProductGateway douyinProductGateway,
            @Qualifier("applicationTaskExecutor") Executor backfillExecutor,
            PlatformTransactionManager transactionManager) {
        this.dryRunProbeService = dryRunProbeService;
        this.productService = productService;
        this.activityMapper = activityMapper;
        this.snapshotMapper = snapshotMapper;
        this.jobLogMapper = jobLogMapper;
        this.syncStateMapper = syncStateMapper;
        this.jobLockService = jobLockService;
        this.productDisplayRuleService = productDisplayRuleService;
        this.douyinProductGateway = douyinProductGateway;
        this.backfillExecutor = backfillExecutor;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setName("product-backfill-batch");
        this.batchTransactionTemplate = template;
    }

    public BackfillResult backfill(BackfillRequest request, UUID requestedBy) {
        NormalizedRequest normalized = normalizeAndValidate(request);
        String jobId = backfillJobPolicy.newJobId(UUID.randomUUID());
        ProductSyncJobLog jobLog = startJob(jobId, normalized, requestedBy);
        return executeBackfillWorkflow(jobId, normalized, jobLog);
    }

    public BackfillAsyncResponse backfillAsync(BackfillRequest request, UUID requestedBy) {
        NormalizedRequest normalized = normalizeAndValidate(request);
        String idempotencyKey = backfillJobPolicy.asyncIdempotencyKey(toBackfillRequestIdentity(normalized, requestedBy));
        ProductSyncJobLog existingRunning = jobLogMapper.selectLatestRunningByAsyncIdempotencyKey(idempotencyKey);
        if (existingRunning != null && StringUtils.hasText(existingRunning.getJobId())) {
            log.info("ProductActivityBackfillService duplicate async request found in job log, existingJobId={}",
                    existingRunning.getJobId());
            return new BackfillAsyncResponse(existingRunning.getJobId(), ProductBackfillJobPolicy.STATUS_RUNNING);
        }
        String jobId = backfillJobPolicy.newJobId(UUID.randomUUID());
        String existingJobId = runningAsyncJobIds.putIfAbsent(idempotencyKey, jobId);
        if (existingJobId != null) {
            log.info("ProductActivityBackfillService duplicate async request ignored, existingJobId={}", existingJobId);
            return new BackfillAsyncResponse(existingJobId, ProductBackfillJobPolicy.STATUS_RUNNING);
        }
        ProductSyncJobLog jobLog;
        try {
            jobLog = startJob(jobId, normalized, requestedBy, idempotencyKey);
        } catch (RuntimeException ex) {
            runningAsyncJobIds.remove(idempotencyKey, jobId);
            throw ex;
        }
        try {
            CompletableFuture.runAsync(() -> {
                        try {
                            executeBackfillWorkflow(jobId, normalized, jobLog);
                        } finally {
                            runningAsyncJobIds.remove(idempotencyKey, jobId);
                        }
                    },
                    backfillExecutor)
                    .exceptionally(ex -> {
                        // executeBackfillWorkflow 内部已 finishJob()，此处仅消费 Future 异常避免 unhandled 警告。
                        log.warn("backfillAsync job completed with error, jobId={}, message={}", jobId, ex.getMessage());
                        return null;
                    });
        } catch (RuntimeException ex) {
            String stopReason = stopReasonForException(ex);
            String errorMessage = buildFailureErrorMessage(
                    jobId,
                    null,
                    null,
                    stopReason,
                    ex,
                    null);
            finishJob(jobLog, failedResult(jobId, normalized, stopReason), "FAILED", errorMessage, 0, 0);
            runningAsyncJobIds.remove(idempotencyKey, jobId);
            throw ex;
        }
        return new BackfillAsyncResponse(jobId, ProductBackfillJobPolicy.STATUS_RUNNING);
    }

    private BackfillResult executeBackfillWorkflow(
            String jobId,
            NormalizedRequest normalized,
            ProductSyncJobLog jobLog) {
        try {
            if (normalized.dryRun()) {
                BackfillResult dryRunResult = runDryRun(jobId, normalized, jobLog);
                String status = backfillJobPolicy.statusFromCounts(dryRunResult.activitiesScanned(),
                        dryRunResult.activitiesSuccess(),
                        dryRunResult.activitiesIncomplete(),
                        dryRunResult.activitiesFailed());
                String dryRunErrorMessage = null;
                if (!"SUCCESS".equals(status)) {
                    String stopReason = dominantStopReason(dryRunResult.stopReasonStats());
                    String rawCause = StringUtils.hasText(stopReason)
                            ? normalizeRawCause(stopReason)
                            : STOP_REASON_UNKNOWN_ERROR;
                    dryRunErrorMessage = buildFailureErrorMessage(
                            jobId,
                            null,
                            normalized.scope(),
                            stopReason,
                            null,
                            null,
                            rawCause,
                            "dry run failed");
                }
                finishJob(jobLog, dryRunResult, status, dryRunErrorMessage, 0, 0);
                return dryRunResult;
            }
            return runRealBackfillWithLocks(jobId, normalized, jobLog);
        } catch (RuntimeException ex) {
            log.error("ProductActivityBackfillService job failed, jobId={}, message={}", jobId, ex.getMessage(), ex);
            String stopReason = stopReasonForException(ex);
            String errorMessage = buildFailureErrorMessage(
                    jobId,
                    null,
                    null,
                    stopReason,
                    ex,
                    null);
            finishJob(jobLog, failedResult(jobId, normalized, stopReason), "FAILED", errorMessage, 0, 0);
            throw ex;
        } catch (Throwable ex) {
            log.error("ProductActivityBackfillService job fatal, jobId={}, message={}", jobId, ex.getMessage(), ex);
            String stopReason = stopReasonForException(ex);
            String errorMessage = buildFailureErrorMessage(
                    jobId,
                    null,
                    null,
                    stopReason,
                    ex,
                    null);
            finishJob(jobLog, failedResult(jobId, normalized, stopReason), "FAILED", errorMessage, 0, 0);
            throw new RuntimeException(ex);
        }
    }

    private NormalizedRequest normalizeAndValidate(BackfillRequest request) {
        NormalizedRequest normalized = normalize(request);
        if (!normalized.dryRun() && !fullBackfillEnabled) {
            throw BusinessException.stateInvalid("活动商品 full backfill 已被配置关闭");
        }
        if (!normalized.dryRun() && !normalized.confirm()) {
            throw BusinessException.param("真实 backfill 必须显式 confirm=true");
        }
        return normalized;
    }

    private BackfillResult runDryRun(String jobId, NormalizedRequest request, ProductSyncJobLog jobLog) {
        AtomicReference<ProductSyncJobLog> jobLogRef = new AtomicReference<>(jobLog);
        int activitiesTotal = knownActivitiesTotal(request);
        ProductSyncDryRunProbeService.FullDryRunResult dryRun = dryRunProbeService.fullDryRun(
                new ProductSyncDryRunProbeService.FullDryRunRequest(
                        request.scope(),
                        request.activityIds(),
                        request.maxActivities(),
                        request.pageSize(),
                        request.maxPagesPerActivity(),
                        request.maxRowsPerActivity(),
                        true),
                (activityIndex, activityResult) -> {
                    ProductSyncJobLog current = jobLogRef.get();
                    if (current != null) {
                        if (activitiesTotal > 0) {
                            jobLogRef.set(updateProgressMetadata(
                                    current,
                                    activityResult.activityId(),
                                    activitiesTotal,
                                    Math.max(0, activityIndex - 1)));
                        } else {
                            jobLogRef.set(updateProgressMetadata(current, activityResult.activityId()));
                        }
                    }
                });
        return new BackfillResult(
                jobId,
                true,
                request.scope(),
                dryRun.activitiesScanned(),
                dryRun.activitiesSuccess(),
                dryRun.activitiesIncomplete(),
                dryRun.activitiesFailed(),
                dryRun.apiFetchedRows(),
                dryRun.apiDistinctProductIds(),
                dryRun.dbRowsForScannedActivities(),
                dryRun.estimatedGapRows(),
                0,
                0,
                0,
                dryRun.activitiesFailed(),
                dryRun.stopReasonStats(),
                dryRun.topGapActivities(),
                0L,
                0L,
                0);
    }

    private BackfillResult runRealBackfillWithLocks(String jobId, NormalizedRequest request, ProductSyncJobLog jobLog) {
        // 真实写库必须先拿 PRODUCT_BACKFILL_GLOBAL 锁，与 ProductActivitySyncJob 互斥。
        String globalLockKey = JobLockKeys.PRODUCT_BACKFILL_GLOBAL;
        if (!jobLockService.tryAcquire(globalLockKey, BACKFILL_LOCK_TTL)) {
            String errorMessage = buildFailedLockErrorMessage(jobId, null, "GLOBAL", globalLockKey, "全局回填锁被占用");
            log.warn("ProductActivityBackfillService skipped, global backfill lock held, jobId={}, error={}", jobId, errorMessage);
            BackfillResult locked = buildLockedResult(jobId, request);
            finishJob(jobLog, locked, "FAILED_LOCKED", errorMessage, 0, 0);
            return locked;
        }
        long totalLockWaitCount = 0L;
        long totalDeadlockRetryCount = 0L;
        List<String> failureMessages = new java.util.ArrayList<>();
        try {
            List<String> activityIds = resolveActivityIds(request);
            long dbRowsBefore = activityIds.isEmpty() ? 0L : snapshotMapper.countActiveRowsByActivityIds(activityIds);
            int success = 0;
            int incomplete = 0;
            int failed = 0;
            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            int unchanged = 0;
            long fetchedRows = 0L;
            long distinctProductIds = 0L;
            Map<String, Long> stopReasonStats = new LinkedHashMap<>();

            // 活动列表按 activityId 升序，固定跨活动的锁顺序。
            List<String> sortedActivityIds = new ArrayList<>(activityIds);
            sortedActivityIds.sort(Comparator.naturalOrder());

            for (int index = 0; index < sortedActivityIds.size(); index++) {
                String activityId = sortedActivityIds.get(index);
                jobLog = updateProgressMetadata(jobLog, activityId, sortedActivityIds.size(), index);
                String activityLockKey = JobLockKeys.productBackfillActivityLock(activityId);
                if (!jobLockService.tryAcquire(activityLockKey, BACKFILL_LOCK_TTL)) {
                    totalLockWaitCount++;
                    String errorMessage = buildFailedLockErrorMessage(
                            jobId,
                            activityId,
                            "ACTIVITY",
                            activityLockKey,
                            "活动回填锁被占用");
                    log.warn("ProductActivityBackfillService activity lock held, skip activityId={}, jobId={}, error={}",
                            activityId, jobId, errorMessage);
                    failed++;
                    stopReasonStats.merge(STOP_REASON_FAILED_LOCKED, 1L, Long::sum);
                    upsertFailedActivityState(activityId, request.scope(), STOP_REASON_FAILED_LOCKED, errorMessage);
                    failureMessages.add(errorMessage);
                    continue;
                }
                long activityLockWait = 0L;
                long activityRetry = 0L;
                try {
                    ActivityBackfillStats stats = runActivityBackfill(request, activityId, jobLog);
                    fetchedRows += stats.fetchedRows;
                    distinctProductIds += stats.distinctProductIds;
                    inserted += stats.inserted;
                    updated += stats.updated;
                    skipped += stats.skipped;
                    unchanged += stats.unchanged;
                    activityLockWait = stats.lockWaitCount;
                    activityRetry = stats.deadlockRetryCount;
                    totalLockWaitCount += activityLockWait;
                    totalDeadlockRetryCount += activityRetry;
                    stopReasonStats.merge(stats.stoppedReason, 1L, Long::sum);
                    String activityErrorMessage = null;
                    if (backfillJobPolicy.isFailedStopReason(stats.stoppedReason)) {
                        String rawCause = StringUtils.hasText(stats.rawCause())
                                ? stats.rawCause()
                                : normalizeRawCause(stats.stoppedReason());
                        activityErrorMessage = buildFailureErrorMessage(
                                jobId,
                                activityId,
                                request.scope(),
                                stats.stoppedReason(),
                                null,
                                null,
                                rawCause,
                                StringUtils.hasText(stats.failureMessage())
                                        ? stats.failureMessage()
                                        : String.format("activity backfill failed: %s", rawCause));
                        failureMessages.add(activityErrorMessage);
                    }
                    if (stats.complete) {
                        success++;
                    } else if (backfillJobPolicy.isFailedStopReason(stats.stoppedReason)) {
                        failed++;
                    } else {
                        incomplete++;
                    }
                    upsertActivityState(activityId, request.scope(), stats, activityErrorMessage);
                } catch (RuntimeException ex) {
                    failed++;
                    String stopReason = stopReasonForException(ex);
                    if (ex instanceof BackfillBatchWriteException batchEx) {
                        totalDeadlockRetryCount += batchEx.retryCount();
                    }
                    stopReasonStats.merge(stopReason, 1L, Long::sum);
                    String failureMessage = buildFailureErrorMessage(
                            jobId,
                            activityId,
                            request.scope(),
                            stopReason,
                            ex,
                            null,
                            null);
                    failureMessages.add(failureMessage);
                    log.error("ProductActivityBackfillService activity failed, activityId={}, jobId={}, stopReason={}, error={}",
                            activityId, jobId, stopReason, failureMessage, ex);
                    upsertFailedActivityState(activityId, request.scope(), stopReason, failureMessage);
                } finally {
                    jobLockService.release(activityLockKey);
                }
            }

            // 事实层写完后，按 displayRefreshMode 决定是否触发展示规则刷新。
            // 触发时也加 PRODUCT_DISPLAY_REFRESH 锁，避免与 ProductDisplayRuleJob 撞车。
            String displayRefreshMode = request.displayRefreshMode() != null
                    ? request.displayRefreshMode()
                    : defaultDisplayRefreshMode;
            if (!"NONE".equalsIgnoreCase(displayRefreshMode) && !"SKIPPED".equalsIgnoreCase(displayRefreshMode)) {
                boolean skipExpired = skipDisplayRefreshForExpiredActivity
                        && hasAnyExpiredActivity(sortedActivityIds);
                if (!skipExpired) {
                    triggerDeferredDisplayRefresh(sortedActivityIds);
                } else {
                    log.info("ProductActivityBackfillService skip display refresh for expired activity, jobId={}", jobId);
                }
            }

            BackfillResult result = new BackfillResult(
                    jobId,
                    false,
                    request.scope(),
                    activityIds.size(),
                    success,
                    incomplete,
                    failed,
                    fetchedRows,
                    distinctProductIds,
                    dbRowsBefore,
                    0,
                    inserted,
                    updated,
                    skipped,
                    failed,
                    stopReasonStats,
                    List.of(),
                    totalLockWaitCount,
                    totalDeadlockRetryCount,
                    unchanged);
            String status = backfillJobPolicy.statusFromCounts(result.activitiesScanned(),
                    result.activitiesSuccess(),
                    result.activitiesIncomplete(),
                    result.activitiesFailed());
            String finalErrorMessage = "SUCCESS".equals(status) ? null : String.join(" | ", failureMessages);
            finishJob(jobLog, result, status, finalErrorMessage, totalLockWaitCount, totalDeadlockRetryCount);
            return result;
        } finally {
            jobLockService.release(globalLockKey);
        }
    }

    private void triggerDeferredDisplayRefresh(List<String> activityIds) {
        String lockKey = JobLockKeys.PRODUCT_DISPLAY_REFRESH;
        if (!jobLockService.tryAcquire(lockKey, BACKFILL_LOCK_TTL)) {
            log.warn("ProductActivityBackfillService skip display refresh, lock held");
            return;
        }
        try {
            for (String activityId : activityIds) {
                try {
                    productDisplayRuleService.repairLibraryStateForActivity(activityId, false, 10000);
                    productDisplayRuleService.applyForActivityId(activityId);
                } catch (RuntimeException ex) {
                    log.warn("ProductActivityBackfillService deferred display refresh failed, activityId={}, message={}",
                            activityId, ex.getMessage());
                }
            }
        } finally {
            jobLockService.release(lockKey);
        }
    }

    private boolean hasAnyExpiredActivity(List<String> activityIds) {
        for (String activityId : activityIds) {
            try {
                ColonelsettlementActivity activity = activityMapper.selectByActivityId(activityId);
                if (activity != null && activity.getEndTime() != null
                        && activity.getEndTime().isBefore(LocalDateTime.now())) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 取不到时不强失败，宁可放过去让展示规则刷新。
            }
        }
        return false;
    }

    private ActivityBackfillStats runActivityBackfill(
            NormalizedRequest request,
            String activityId,
            ProductSyncJobLog jobLog) {
        BatchedBackfillResult batched = runActivityBackfillBatched(request, activityId, jobLog);
        ProductService.ActivityProductRefreshResult result = batched.result();
        return new ActivityBackfillStats(
                result.complete(),
                result.fetchedRows(),
                result.distinctProductIds(),
                result.createdCount(),
                result.updatedCount(),
                result.skippedCount(),
                batched.unchanged(),
                result.stoppedReason(),
                batched.rawCause(),
                batched.failureMessage(),
                result.pagesFetched(),
                batched.lastCursor(),
                0L,
                batched.deadlockRetryCount());
    }

    /**
     * 单 activity backfill 的核心实现。
     *
     * <p>绕开 {@link ProductService#refreshActivitySnapshots} 的大事务，自己分 page 拉取 + 拆 batch +
     * 每 batch 一个 {@link TransactionTemplate} 独立小事务，避免长事务持锁触发 PostgreSQL deadlock。
     * 同时按 {@code product_id} 升序排序后写入，固定事务内的锁顺序。</p>
     */
    private BatchedBackfillResult runActivityBackfillBatched(
            NormalizedRequest request,
            String activityId,
            ProductSyncJobLog jobLog) {
        int pageSize = Math.min(Math.max(request.pageSize(), 1), 20);
        int normalizedMaxPages = Math.max(request.maxPagesPerActivity(), 1);
        int normalizedMaxRows = Math.max(request.maxRowsPerActivity(), 1);
        java.util.concurrent.atomic.AtomicInteger pageCounter = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<ProductSyncJobLog> jobLogRef = new AtomicReference<>(jobLog);
        final int[] inserted = {0};
        final int[] updated = {0};
        final int[] skipped = {0};
        final int[] unchanged = {0};
        final int[] libraryEntryCount = {0};
        final long[] deadlockRetryCount = {0L};
        ActivityProductPaginationRunner.Result pageResult = ActivityProductPaginationRunner.run(
                buildQueryRequest(activityId, pageSize),
                new ActivityProductPaginationRunner.Options(
                        pageSize,
                        normalizedMaxPages,
                        normalizedMaxRows,
                        true),
                pageRequest -> queryActivityProductsWithRetry(pageRequest, pageCounter.getAndIncrement()),
                page -> {
                    // 1. 锁顺序：按 product_id 升序，避免事务间死锁。
                    List<DouyinProductGateway.ActivityProductItem> sortedItems = new ArrayList<>(page.items());
                    sortedItems.sort(Comparator.comparingLong(DouyinProductGateway.ActivityProductItem::productId));
                    // 2. 拆小事务：每 writeBatchSize 一个独立子事务。
                    int batchSize = Math.max(1, writeBatchSize);
                    int pageInserted = 0;
                    int pageUpdated = 0;
                    int pageSkipped = 0;
                    int pageLibraryEntryCount = 0;
                    for (int i = 0; i < sortedItems.size(); i += batchSize) {
                        int end = Math.min(sortedItems.size(), i + batchSize);
                        List<DouyinProductGateway.ActivityProductItem> batch = sortedItems.subList(i, end);
                        // 用独立小事务包住 snapshot upsert + operation state update。
                        BatchWriteResult batchResult = executeBatchWithDeadlockRetry(
                                activityId,
                                batch,
                                batchRetryCount -> {
                                    ProductSyncJobLog current = jobLogRef.get();
                                    if (current != null) {
                                        long totalRetryCount = deadlockRetryCount[0] + batchRetryCount;
                                        try {
                                            jobLogRef.set(updateRetryProgressMetadata(
                                                    current,
                                                    activityId,
                                                    totalRetryCount));
                                        } catch (RuntimeException progressEx) {
                                            log.warn(
                                                    "ProductActivityBackfillService retry progress flush failed, activityId={}, deadlockRetryCount={}, message={}",
                                                    activityId,
                                                    totalRetryCount,
                                                    progressEx.getMessage());
                                        }
                                    }
                                });
                        ProductService.ActivitySnapshotUpsertStats stats = batchResult.stats();
                        deadlockRetryCount[0] += batchResult.deadlockRetryCount();
                        if (stats != null) {
                            inserted[0] += stats.createdCount();
                            updated[0] += stats.updatedCount();
                            skipped[0] += stats.skippedCount();
                            unchanged[0] += stats.unchangedCount();
                            libraryEntryCount[0] += stats.libraryEntryCount();
                            pageInserted += stats.createdCount();
                            pageUpdated += stats.updatedCount();
                            pageSkipped += stats.skippedCount();
                            pageLibraryEntryCount += stats.libraryEntryCount();
                        }
                    }
                    return new ActivityProductPaginationRunner.PageWriteStats(
                            pageInserted,
                            pageUpdated,
                            pageSkipped,
                            pageLibraryEntryCount);
                },
                pageNo -> {
                    if (pageNo == 1 || pageNo % 3 == 0) {
                        ProductSyncJobLog current = jobLogRef.get();
                        if (current != null) {
                            jobLogRef.set(updateProgressMetadata(current, activityId));
                        }
                    }
                    // backfill 写库路径不复用 ProductService 的长事务翻页 sleep，但保留上游 API 节流。
                    sleepBeforeNextBackfillPage(activityId, pageNo - 1);
                });
        // 触发展示规则刷新（如果 displayRefreshMode = IMMEDIATE 才在事实层写完后立即刷）。
        // 默认是 DEFERRED，由 backfill 任务尾部统一处理（见 runRealBackfillWithLocks.triggerDeferredDisplayRefresh）。
        if ("IMMEDIATE".equalsIgnoreCase(request.displayRefreshMode() == null
                ? defaultDisplayRefreshMode : request.displayRefreshMode())) {
            try {
                productDisplayRuleService.repairLibraryStateForActivity(activityId, false, 10000);
                productDisplayRuleService.applyForActivityId(activityId);
            } catch (RuntimeException ex) {
                log.warn("ProductActivityBackfillService immediate display refresh failed, activityId={}, message={}",
                        activityId, ex.getMessage());
            }
        }
        ProductService.ActivityProductRefreshResult refreshResult =
                new ProductService.ActivityProductRefreshResult(
                        pageResult.distinctProductIds(),
                        libraryEntryCount[0],
                        inserted[0],
                        updated[0],
                        skipped[0],
                        pageResult.pagesFetched(),
                        pageResult.fetchedRows(),
                        pageResult.distinctProductIds(),
                        pageResult.duplicateProductIds(),
                        pageResult.stopReason().name(),
                        pageResult.stillHasNextWhenStopped(),
                        pageResult.complete());
        String rawCause = null;
        String failureMessage = null;
        if (ActivityProductPaginationRunner.StopReason.API_ERROR.equals(pageResult.stopReason())
                || ActivityProductPaginationRunner.StopReason.INVALID_RESPONSE.equals(pageResult.stopReason())) {
            String warningsText = String.join(" | ", pageResult.warnings());
            rawCause = normalizeRawCause(pageResult.stopReason().name());
            if (!StringUtils.hasText(rawCause) && StringUtils.hasText(warningsText)) {
                rawCause = warningsText;
            }
            if (StringUtils.hasText(warningsText)) {
                failureMessage = "activity backfill failed: " + warningsText;
            }
        }
        return new BatchedBackfillResult(
                refreshResult,
                deadlockRetryCount[0],
                unchanged[0],
                rawCause,
                failureMessage,
                pageResult.lastCursor());
    }

    private BatchWriteResult executeBatchWithDeadlockRetry(
            String activityId,
            List<DouyinProductGateway.ActivityProductItem> batch,
            LongConsumer onRetry) {
        int maxAttempts = Math.max(1, deadlockRetryMax + 1);
        long retryCount = 0L;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ProductService.ActivitySnapshotUpsertStats stats = batchTransactionTemplate.execute(
                        status -> productService.upsertSnapshotsWithStats(activityId, batch));
                return new BatchWriteResult(stats, retryCount);
            } catch (DataAccessException ex) {
                if (isDeadlockLike(ex) && attempt < maxAttempts) {
                    retryCount++;
                    if (onRetry != null) {
                        onRetry.accept(retryCount);
                    }
                    log.warn("ProductActivityBackfillService batch deadlock-like, activityId={}, attempt={}/{}, message={}",
                            activityId, attempt, maxAttempts, ex.getMessage());
                    sleepBackoff(attempt);
                    continue;
                }
                if (isDeadlockLike(ex)) {
                    throw new BackfillBatchWriteException(STOP_REASON_DEADLOCK_RETRY_EXHAUSTED, retryCount, ex);
                }
                throw ex;
            }
        }
        throw BusinessException.stateInvalid("backfill batch 重试耗尽但无明确错误");
    }

    private String stopReasonForException(Throwable ex) {
        String explicitStopReason = ex instanceof BackfillBatchWriteException batchEx
                && StringUtils.hasText(batchEx.stopReason())
                ? batchEx.stopReason()
                : null;
        return backfillFailurePolicy.stopReasonForException(ex, explicitStopReason);
    }

    private String dominantStopReason(Map<String, Long> stopReasonStats) {
        return backfillFailurePolicy.dominantStopReason(stopReasonStats);
    }

    private String normalizeRawCause(String stopReason) {
        return backfillFailurePolicy.normalizeRawCause(stopReason);
    }

    private String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String lockInfo) {
        return backfillFailurePolicy.buildFailureErrorMessage(
                jobId,
                activityId,
                scope,
                stopReason,
                ex,
                lockInfo,
                null,
                null);
    }

    private String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String lockInfo,
            String explicitRawCause,
            String explicitMessage) {
        return backfillFailurePolicy.buildFailureErrorMessage(
                jobId,
                activityId,
                scope,
                stopReason,
                ex,
                lockInfo,
                explicitRawCause,
                explicitMessage);
    }

    private String buildFailureErrorMessage(
            String jobId,
            String activityId,
            String scope,
            String stopReason,
            Throwable ex,
            String explicitRawCause,
            String explicitMessage) {
        return backfillFailurePolicy.buildFailureErrorMessage(
                jobId,
                activityId,
                scope,
                stopReason,
                ex,
                null,
                explicitRawCause,
                explicitMessage);
    }

    private String buildFailedLockErrorMessage(String jobId, String activityId, String lockScope, String lockKey,
                                              String extraMessage) {
        JobLockSnapshot ownerSnapshot = lockSnapshot(lockKey);
        ProductBackfillFailurePolicy.JobLockSnapshot policySnapshot = ownerSnapshot == null
                ? null
                : new ProductBackfillFailurePolicy.JobLockSnapshot(
                ownerSnapshot.lockKey(),
                ownerSnapshot.ownerJobId(),
                ownerSnapshot.ownerActivityId(),
                ownerSnapshot.scope(),
                ownerSnapshot.ownerLockKey(),
                ownerSnapshot.lockValue(),
                ownerSnapshot.ttlSeconds(),
                ownerSnapshot.acquiredAt());
        return backfillFailurePolicy.buildFailedLockErrorMessage(
                jobId,
                activityId,
                lockScope,
                lockKey,
                policySnapshot,
                extraMessage);
    }

    private JobLockSnapshot lockSnapshot(String lockKey) {
        String value = jobLockService.currentLockValue(lockKey);
        long ttl = jobLockService.currentLockTtlSeconds(lockKey);
        return parseLockSnapshot(lockKey, value, ttl);
    }

    private JobLockSnapshot parseLockSnapshot(String lockKey, String value, long ttl) {
        if (!StringUtils.hasText(value)) {
            return new JobLockSnapshot(lockKey, null, null, null, null, null, ttl, null);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(value, Map.class);
            return new JobLockSnapshot(
                    lockKey,
                    asText(map.get("ownerJobId")),
                    firstNonBlank(asText(map.get("ownerActivityId")), asText(map.get("activityId"))),
                    firstNonBlank(asText(map.get("scope")), "UNKNOWN"),
                    firstNonBlank(asText(map.get("lockKey")), lockKey),
                    value,
                    ttl,
                    asText(map.get("acquiredAt")));
        } catch (JsonProcessingException ex) {
            return new JobLockSnapshot(lockKey, null, null, null, null, value, ttl, null);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private DouyinProductGateway.ActivityProductListResult queryActivityProductsWithRetry(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int pageNo) {
        // backfill 路径直接调一次 gateway；DB deadlock retry 在 batch 写库事务内处理，不重新拉取本页。
        return douyinProductGateway.queryActivityProducts(request);
    }

    private void sleepBackoff(int attempt) {
        long base = 200L * (1L << Math.min(attempt - 1, 3)); // 200 / 400 / 800 / 1600
        long jitter = ThreadLocalRandom.current().nextLong(0, 100L);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.stateInvalid("backfill retry 被中断");
        }
    }

    private void sleepBeforeNextBackfillPage(String activityId, int pageNo) {
        long millis = Math.max(0L, Math.min(1000L, backfillApiSleepMs));
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("ProductActivityBackfillService interrupted before next page, activityId={}, page={}",
                    activityId, pageNo + 1);
            throw BusinessException.stateInvalid("backfill page sleep 被中断");
        }
    }

    private boolean isDeadlockLike(Throwable ex) {
        return backfillFailurePolicy.isDeadlockLike(ex);
    }

    private List<String> resolveActivityIds(NormalizedRequest request) {
        return activityMapper.selectActivityIdsForProductSyncProbe(
                mapperScope(request.scope()),
                request.maxActivities(),
                recentSince(request.scope()),
                request.activityIds());
    }

    private LocalDateTime recentSince(String scope) {
        return switch (scope) {
            case "RECENT_30D" -> LocalDateTime.now().minusDays(30);
            case "RECENT_90D" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId, int pageSize) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                null, activityId, 4L, 1L, pageSize, null, null, null, null, 1L, null, null);
    }

    private void upsertActivityState(
            String activityId,
            String scope,
            ActivityBackfillStats stats,
            String errorMessage) {
        ProductActivitySyncState state = new ProductActivitySyncState();
        LocalDateTime now = LocalDateTime.now();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setScope(scope);
        state.setLastAttemptAt(now);
        state.setLastSuccessAt(stats.complete ? now : null);
        state.setLastStatus(backfillJobPolicy.statusForStopReason(stats.stoppedReason, stats.complete));
        state.setLastStopReason(stats.stoppedReason);
        state.setLastCursor(stats.lastCursor);
        state.setLastPage(stats.lastPage);
        state.setLastFetchedRows(stats.fetchedRows);
        state.setLastDistinctProductIds(stats.distinctProductIds);
        state.setLastInserted(stats.inserted);
        state.setLastUpdated(stats.updated);
        state.setLastSkipped(stats.skipped);
        state.setLastFailed(backfillJobPolicy.isFailedStopReason(stats.stoppedReason) ? 1 : 0);
        state.setConsecutiveFailures(stats.complete ? 0 : 1);
        state.setLastErrorMessage(errorMessage);
        state.setCreateTime(now);
        state.setUpdateTime(now);
        syncStateMapper.upsert(state);
    }

    private void upsertFailedActivityState(String activityId, String scope, String errorMessage) {
        upsertFailedActivityState(activityId, scope, ActivityProductPaginationRunner.StopReason.API_ERROR.name(), errorMessage);
    }

    private void upsertFailedActivityState(String activityId, String scope, String stopReason, String errorMessage) {
        ActivityBackfillStats failed = new ActivityBackfillStats(
                false, 0L, 0L, 0, 0, 0, 0,
                stopReason,
                normalizeRawCause(stopReason),
                null,
                0,
                null,
                0L,
                0L);
        upsertActivityState(activityId, scope, failed, errorMessage);
    }

    private ProductSyncJobLog startJob(String jobId, NormalizedRequest request, UUID requestedBy) {
        return startJob(jobId, request, requestedBy, null);
    }

    private ProductSyncJobLog startJob(
            String jobId,
            NormalizedRequest request,
            UUID requestedBy,
            String asyncIdempotencyKey) {
        ProductSyncJobLog log = new ProductSyncJobLog();
        LocalDateTime now = LocalDateTime.now();
        log.setId(UUID.randomUUID());
        log.setJobId(jobId);
        log.setJobType("sync_activity_product_full_backfill");
        log.setScope(request.scope());
        log.setDryRun(request.dryRun());
        log.setStatus("RUNNING");
        log.setRequestedBy(requestedBy);
        String requestParamsJson = backfillJobMetadata.started(toJson(request), asyncIdempotencyKey, now);
        int activitiesTotal = knownActivitiesTotal(request);
        if (activitiesTotal > 0) {
            requestParamsJson = backfillJobMetadata.progress(requestParamsJson, "", activitiesTotal, 0, now);
        }
        log.setRequestParamsJson(requestParamsJson);
        log.setStartedAt(now);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        jobLogMapper.insert(log);
        return log;
    }

    private void finishJob(ProductSyncJobLog log, BackfillResult result, String status, String errorMessage,
                           long lockWaitCount, long deadlockRetryCount) {
        log.setStatus(status);
        log.setFinishedAt(LocalDateTime.now());
        log.setActivitiesScanned(result.activitiesScanned());
        log.setActivitiesSuccess(result.activitiesSuccess());
        log.setActivitiesIncomplete(result.activitiesIncomplete());
        log.setActivitiesFailed(result.activitiesFailed());
        log.setApiFetchedRows(result.apiFetchedRows());
        log.setApiDistinctProductIds(result.apiDistinctProductIds());
        log.setInserted(result.inserted());
        log.setUpdated(result.updated());
        log.setSkipped(result.skipped());
        log.setFailed(result.failed());
        log.setStopReasonStatsJson(toJson(result.stopReasonStats()));
        log.setErrorMessage(errorMessage);
        log.setUpdateTime(LocalDateTime.now());
        // 把 lockWaitCount / deadlockRetryCount 写入 requestParamsJson 的 metadata 子段，
        // 现有 schema 不新增列，避免破坏既有 reader。
        log.setRequestParamsJson(backfillJobMetadata.finished(
                log.getRequestParamsJson(),
                new ProductBackfillJobMetadata.FinishMetrics(
                        lockWaitCount,
                        deadlockRetryCount,
                        result.dbRowsBefore(),
                        result.estimatedGapRows(),
                        result.unchanged()),
                LocalDateTime.now()));
        jobLogMapper.updateById(log);
    }

    private ProductSyncJobLog updateProgressMetadata(ProductSyncJobLog log, String currentActivityId) {
        log.setRequestParamsJson(backfillJobMetadata.progress(
                log.getRequestParamsJson(),
                currentActivityId,
                LocalDateTime.now()));
        log.setUpdateTime(LocalDateTime.now());
        jobLogMapper.updateById(log);
        return log;
    }

    private ProductSyncJobLog updateProgressMetadata(
            ProductSyncJobLog log,
            String currentActivityId,
            int activitiesTotal,
            int activitiesProcessed) {
        log.setRequestParamsJson(backfillJobMetadata.progress(
                log.getRequestParamsJson(),
                currentActivityId,
                activitiesTotal,
                activitiesProcessed,
                LocalDateTime.now()));
        log.setUpdateTime(LocalDateTime.now());
        jobLogMapper.updateById(log);
        return log;
    }

    private ProductSyncJobLog updateRetryProgressMetadata(
            ProductSyncJobLog log,
            String currentActivityId,
            long deadlockRetryCount) {
        log.setRequestParamsJson(backfillJobMetadata.retryProgress(
                log.getRequestParamsJson(),
                currentActivityId,
                deadlockRetryCount,
                LocalDateTime.now()));
        log.setUpdateTime(LocalDateTime.now());
        jobLogMapper.updateById(log);
        return log;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int knownActivitiesTotal(NormalizedRequest request) {
        if (request == null || !"CUSTOM_ACTIVITY_IDS".equals(request.scope())) {
            return 0;
        }
        return request.activityIds() == null ? 0 : request.activityIds().size();
    }

    private BackfillResult buildLockedResult(String jobId, NormalizedRequest request) {
        Map<String, Long> stopReasonStats = new LinkedHashMap<>();
        stopReasonStats.put("FAILED_LOCKED", 1L);
        return new BackfillResult(
                jobId,
                false,
                request.scope(),
                0,
                0,
                0,
                1,
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                1,
                stopReasonStats,
                List.of(),
                0L,
                0L,
                0);
    }

    private BackfillResult failedResult(String jobId, NormalizedRequest request) {
        return failedResult(jobId, request, ActivityProductPaginationRunner.StopReason.UNKNOWN.name());
    }

    private BackfillResult failedResult(String jobId, NormalizedRequest request, String stopReason) {
        String normalizedStopReason = StringUtils.hasText(stopReason)
                ? stopReason
                : ActivityProductPaginationRunner.StopReason.UNKNOWN.name();
        return new BackfillResult(
                jobId,
                request.dryRun(),
                request.scope(),
                0,
                0,
                0,
                1,
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                1,
                Map.of(normalizedStopReason, 1L),
                List.of(),
                0L,
                0L,
                0);
    }

    private NormalizedRequest normalize(BackfillRequest request) {
        BackfillRequest safe = request == null
                ? new BackfillRequest(null, List.of(), null, null, null, null, true, false, null)
                : request;
        String scope = normalizeScope(safe.scope());
        List<String> activityIds = safe.activityIds() == null
                ? List.of()
                : safe.activityIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if ("CUSTOM_ACTIVITY_IDS".equals(scope) && activityIds.isEmpty()) {
            throw BusinessException.param("CUSTOM_ACTIVITY_IDS 必须提供 activityIds");
        }
        return new NormalizedRequest(
                scope,
                activityIds,
                normalizePositive(safe.pageSize(), DEFAULT_PAGE_SIZE, 20),
                normalizePositive(safe.maxActivities(), DEFAULT_MAX_ACTIVITIES, MAX_ACTIVITIES),
                normalizePositive(safe.maxPagesPerActivity(), DEFAULT_MAX_PAGES, Integer.MAX_VALUE),
                normalizePositive(safe.maxRowsPerActivity(), DEFAULT_MAX_ROWS, Integer.MAX_VALUE),
                safe.dryRun() == null || safe.dryRun(),
                Boolean.TRUE.equals(safe.confirm()),
                safe.displayRefreshMode());
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "RECENT_30D";
        }
        String normalized = scope.trim().toUpperCase();
        if ("CUSTOM".equals(normalized)) {
            return "CUSTOM_ACTIVITY_IDS";
        }
        return normalized;
    }

    private String mapperScope(String scope) {
        return "CUSTOM_ACTIVITY_IDS".equals(scope) ? "CUSTOM" : scope;
    }

    private int normalizePositive(Integer value, int defaultValue, int maxValue) {
        int normalized = value == null || value <= 0 ? defaultValue : value;
        return Math.min(Math.max(normalized, 1), maxValue);
    }

    private ProductBackfillJobPolicy.BackfillRequestIdentity toBackfillRequestIdentity(
            NormalizedRequest request,
            UUID requestedBy) {
        return new ProductBackfillJobPolicy.BackfillRequestIdentity(
                request.scope(),
                request.activityIds(),
                request.pageSize(),
                request.maxActivities(),
                request.maxPagesPerActivity(),
                request.maxRowsPerActivity(),
                request.dryRun(),
                request.confirm(),
                request.displayRefreshMode(),
                requestedBy);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private record ActivityBackfillStats(
            boolean complete,
            long fetchedRows,
            long distinctProductIds,
            int inserted,
            int updated,
            int skipped,
            int unchanged,
            String stoppedReason,
            String rawCause,
            String failureMessage,
            int lastPage,
            String lastCursor,
            long lockWaitCount,
            long deadlockRetryCount) {
    }

    private record BatchedBackfillResult(
            ProductService.ActivityProductRefreshResult result,
            long deadlockRetryCount,
            int unchanged,
            String rawCause,
            String failureMessage,
            String lastCursor) {
    }

    private record BatchWriteResult(
            ProductService.ActivitySnapshotUpsertStats stats,
            long deadlockRetryCount) {
    }

    private static final record JobLockSnapshot(
            String lockKey,
            String ownerJobId,
            String ownerActivityId,
            String scope,
            String ownerLockKey,
            String lockValue,
            long ttlSeconds,
            String acquiredAt) {
    }

    private static final class BackfillBatchWriteException extends RuntimeException {
        private final String stopReason;
        private final long retryCount;

        private BackfillBatchWriteException(String stopReason, long retryCount, Throwable cause) {
            super(cause == null ? stopReason : cause.getMessage(), cause);
            this.stopReason = stopReason;
            this.retryCount = retryCount;
        }

        private String stopReason() {
            return stopReason;
        }

        private long retryCount() {
            return retryCount;
        }
    }

    public record BackfillRequest(
            String scope,
            List<String> activityIds,
            Integer pageSize,
            Integer maxActivities,
            Integer maxPagesPerActivity,
            Integer maxRowsPerActivity,
            Boolean dryRun,
            Boolean confirm,
            String displayRefreshMode) {
    }

    public record BackfillResult(
            String jobId,
            boolean dryRun,
            String scope,
            int activitiesScanned,
            int activitiesSuccess,
            int activitiesIncomplete,
            int activitiesFailed,
            long apiFetchedRows,
            long apiDistinctProductIds,
            long dbRowsBefore,
            long estimatedGapRows,
            int inserted,
            int updated,
            int skipped,
            int failed,
            Map<String, Long> stopReasonStats,
            List<ProductSyncDryRunProbeService.ActivityDryRunResult> topGapActivities,
            long lockWaitCount,
            long deadlockRetryCount,
            int unchanged) {
    }

    public record BackfillAsyncResponse(
            String jobId,
            String status) {
    }

    private record NormalizedRequest(
            String scope,
            List<String> activityIds,
            int pageSize,
            int maxActivities,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            boolean dryRun,
            boolean confirm,
            String displayRefreshMode) {
    }
}
