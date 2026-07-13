package com.colonel.saas.job;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.DistributedConcurrencyLimiter;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 活动商品快照定时同步任务。
 */
@Slf4j
@Component
public class ProductActivitySyncJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int QPS_GUARD_SLEEP_MS = 2000;
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_ACTIVITIES_PER_RUN = 200;
    private static final int MAX_PARALLELISM = 2;

    private final ProductService productService;
    private final DistributedJobLockService jobLockService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final int qpsGuardSleepMillis;
    private final Executor syncExecutor;
    private final DistributedConcurrencyLimiter concurrencyLimiter;

    @Value("${product.activity.sync.enabled:false}")
    private boolean enabled;
    @Value("${product.sync.activityProduct.incrementalEnabled:${product.activity.sync.enabled:false}}")
    private boolean incrementalEnabled = true;
    @Value("${product.activity.sync.cron:0 */5 * * * ?}")
    private String cronExpression;
    @Value("${product.activity.sync.batch-size:20}")
    private int batchSize;
    @Value("${product.activity.sync.whitelist-activities:}")
    private String whitelistActivities;
    @Value("${product.activity.sync.parallelism:2}")
    private int parallelism = 2;
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int pageSize;
    @Value("${product.sync.activityProduct.maxActivitiesPerRun:${product.activity.sync.batch-size:20}}")
    private int maxActivitiesPerRun;

    @Autowired
    public ProductActivitySyncJob(
            ProductService productService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper,
            @Qualifier("applicationTaskExecutor") Executor syncExecutor,
            DistributedConcurrencyLimiter concurrencyLimiter) {
        this(
                productService,
                jobLockService,
                activityMapper,
                QPS_GUARD_SLEEP_MS,
                syncExecutor,
                concurrencyLimiter);
    }

    ProductActivitySyncJob(
            ProductService productService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper,
            int qpsGuardSleepMillis) {
        this(productService, jobLockService, activityMapper, qpsGuardSleepMillis, Runnable::run, null);
    }

    ProductActivitySyncJob(
            ProductService productService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper,
            int qpsGuardSleepMillis,
            Executor syncExecutor,
            DistributedConcurrencyLimiter concurrencyLimiter) {
        this.productService = productService;
        this.jobLockService = jobLockService;
        this.activityMapper = activityMapper;
        this.qpsGuardSleepMillis = Math.max(0, qpsGuardSleepMillis);
        this.syncExecutor = syncExecutor;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @PostConstruct
    void logStartupConfig() {
        log.info("ProductActivitySyncJob config: enabled={}, cron={}, batchSize={}, parallelism={}, whitelist={}",
                enabled,
                cronExpression,
                batchSize,
                normalizedParallelism(),
                whitelistActivities.isBlank() ? "(all active)" : whitelistActivities);
    }

    @Scheduled(cron = "${product.activity.sync.cron:0 */5 * * * ?}")
    public void syncAll() {
        if (!enabled || !incrementalEnabled) {
            log.debug("ProductActivitySyncJob skipped (disabled by config)");
            return;
        }
        String syncOwner = "scheduled:" + java.util.UUID.randomUUID();
        // Phase 4-1.5 deadlock 修复：定时同步先抢全局 backfill 锁，避免与 backfill 任务并发写入 product_operation_state。
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, LOCK_TTL, syncOwner)) {
            log.info("ProductActivitySyncJob skipped, backfill global lock held (likely a backfill job in progress)");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_ACTIVITY_SYNC, LOCK_TTL, syncOwner)) {
            log.info("ProductActivitySyncJob skipped, activity sync lock held by another node");
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, syncOwner);
            return;
        }
        try {
            List<String> activityIds = resolveActivityIds();
            ActivitySyncSummary summary = syncActivities(activityIds, syncOwner);
            log.info("ProductActivitySyncJob finished, ok={}, fail={}, skipped={}, parallelism={}",
                    summary.ok(),
                    summary.fail(),
                    summary.skipped(),
                    normalizedParallelism());
        } finally {
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_ACTIVITY_SYNC, syncOwner);
            jobLockService.releaseWithOwner(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, syncOwner);
        }
    }

    private ActivitySyncSummary syncActivities(List<String> activityIds, String syncOwner) {
        int ok = 0;
        int fail = 0;
        int skipped = 0;
        int batchParallelism = normalizedParallelism();
        for (int start = 0; start < activityIds.size(); start += batchParallelism) {
            int end = Math.min(start + batchParallelism, activityIds.size());
            List<CompletableFuture<ActivitySyncOutcome>> futures = new ArrayList<>(end - start);
            for (String activityId : activityIds.subList(start, end)) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> syncActivity(activityId, syncOwner),
                        syncExecutor));
            }
            for (CompletableFuture<ActivitySyncOutcome> future : futures) {
                ActivitySyncOutcome outcome = future.join();
                if (outcome == ActivitySyncOutcome.SUCCESS) {
                    ok++;
                } else if (outcome == ActivitySyncOutcome.FAILED) {
                    fail++;
                } else {
                    skipped++;
                }
            }
            if (end < activityIds.size() && !sleepBeforeNextBatch()) {
                break;
            }
        }
        return new ActivitySyncSummary(ok, fail, skipped);
    }

    private ActivitySyncOutcome syncActivity(String activityId, String syncOwner) {
        String activityLockKey = JobLockKeys.productBackfillActivityLock(activityId);
        String activityOwner = syncOwner + ":" + activityId;
        boolean acquiredActivityLock = false;
        boolean acquiredConcurrencySlot = false;
        try {
            acquiredActivityLock = jobLockService.tryAcquire(activityLockKey, LOCK_TTL, activityOwner);
            if (!acquiredActivityLock) {
                log.info("ProductActivitySyncJob skip activity, backfill activity lock held, activityId={}", activityId);
                return ActivitySyncOutcome.SKIPPED;
            }
            if (concurrencyLimiter != null) {
                acquiredConcurrencySlot = concurrencyLimiter.tryAcquire(activityOwner, LOCK_TTL);
                if (!acquiredConcurrencySlot) {
                    log.info("ProductActivitySyncJob skip activity, upstream concurrency slots full, activityId={}",
                            activityId);
                    return ActivitySyncOutcome.SKIPPED;
                }
            }
            ProductService.ActivityProductRefreshResult result =
                    productService.refreshActivitySnapshots(buildQueryRequest(activityId));
            if (result.complete()) {
                activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
            }
            log.info("ProductActivitySyncJob activity synced, activityId={}, syncedProductCount={}, libraryEntryCount={}, createdCount={}, updatedCount={}, skippedCount={}, pagesFetched={}, fetchedRows={}, stoppedReason={}, stillHasNextWhenStopped={}, complete={}",
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
            return result.complete() ? ActivitySyncOutcome.SUCCESS : ActivitySyncOutcome.FAILED;
        } catch (Exception ex) {
            log.warn("ProductActivitySyncJob activity sync failed, activityId={}", activityId, ex);
            return ActivitySyncOutcome.FAILED;
        } finally {
            if (acquiredConcurrencySlot && concurrencyLimiter != null) {
                try {
                    concurrencyLimiter.releaseWithOwner(activityOwner);
                } catch (Exception ex) {
                    log.warn("ProductActivitySyncJob concurrency slot release failed, activityId={}, message={}",
                            activityId,
                            ex.getMessage());
                }
            }
            if (acquiredActivityLock) {
                try {
                    jobLockService.releaseWithOwner(activityLockKey, activityOwner);
                } catch (Exception ex) {
                    log.warn("ProductActivitySyncJob activity lock release failed, activityId={}, message={}",
                            activityId,
                            ex.getMessage());
                }
            }
        }
    }

    private List<String> resolveActivityIds() {
        if (StringUtils.hasText(whitelistActivities)) {
            return Arrays.stream(whitelistActivities.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return activityMapper.selectActiveActivityIds(
                normalizedMaxActivitiesPerRun(),
                LocalDateTime.now().minusMinutes(30));
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                null,
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

    private int normalizedMaxActivitiesPerRun() {
        int fallback = batchSize > 0 ? batchSize : 20;
        int configured = maxActivitiesPerRun > 0 ? maxActivitiesPerRun : fallback;
        return Math.min(Math.max(configured, MIN_BATCH_SIZE), MAX_ACTIVITIES_PER_RUN);
    }

    private int normalizedPageSize() {
        return Math.min(Math.max(pageSize, MIN_BATCH_SIZE), MAX_PAGE_SIZE);
    }

    private int normalizedBatchSize() {
        return Math.min(Math.max(batchSize, MIN_BATCH_SIZE), MAX_ACTIVITIES_PER_RUN);
    }

    private int normalizedParallelism() {
        return Math.min(Math.max(parallelism, MIN_BATCH_SIZE), MAX_PARALLELISM);
    }

    private boolean sleepBeforeNextBatch() {
        if (qpsGuardSleepMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(qpsGuardSleepMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("ProductActivitySyncJob interrupted before next activity batch");
            return false;
        }
    }

    private enum ActivitySyncOutcome {
        SUCCESS,
        FAILED,
        SKIPPED
    }

    private record ActivitySyncSummary(int ok, int fail, int skipped) {
    }
}
