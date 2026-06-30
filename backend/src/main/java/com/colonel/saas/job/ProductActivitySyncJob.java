package com.colonel.saas.job;

import com.colonel.saas.domain.product.application.ProductActivitySyncApplicationService;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 活动商品快照定时同步任务。
 */
@Slf4j
@Component
public class ProductActivitySyncJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int QPS_GUARD_SLEEP_MS = 2000;
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_ACTIVITIES_PER_RUN = 200;

    private final ProductActivitySyncApplicationService productActivitySyncApplicationService;
    private final DistributedJobLockService jobLockService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final int qpsGuardSleepMillis;

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
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int pageSize;
    @Value("${product.sync.activityProduct.maxActivitiesPerRun:${product.activity.sync.batch-size:20}}")
    private int maxActivitiesPerRun;

    @Autowired
    public ProductActivitySyncJob(
            ProductActivitySyncApplicationService productActivitySyncApplicationService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper) {
        this(productActivitySyncApplicationService, jobLockService, activityMapper, QPS_GUARD_SLEEP_MS);
    }

    ProductActivitySyncJob(
            ProductActivitySyncApplicationService productActivitySyncApplicationService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper,
            int qpsGuardSleepMillis) {
        this.productActivitySyncApplicationService = productActivitySyncApplicationService;
        this.jobLockService = jobLockService;
        this.activityMapper = activityMapper;
        this.qpsGuardSleepMillis = Math.max(0, qpsGuardSleepMillis);
    }

    @PostConstruct
    void logStartupConfig() {
        log.info("ProductActivitySyncJob config: enabled={}, cron={}, batchSize={}, whitelist={}",
                enabled,
                cronExpression,
                batchSize,
                whitelistActivities.isBlank() ? "(all active)" : whitelistActivities);
    }

    @Scheduled(cron = "${product.activity.sync.cron:0 */5 * * * ?}")
    public void syncAll() {
        if (!enabled || !incrementalEnabled) {
            log.debug("ProductActivitySyncJob skipped (disabled by config)");
            return;
        }
        // Phase 4-1.5 deadlock 修复：定时同步先抢全局 backfill 锁，避免与 backfill 任务并发写入 product_operation_state。
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_BACKFILL_GLOBAL, LOCK_TTL)) {
            log.info("ProductActivitySyncJob skipped, backfill global lock held (likely a backfill job in progress)");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_ACTIVITY_SYNC, LOCK_TTL)) {
            log.info("ProductActivitySyncJob skipped, activity sync lock held by another node");
            jobLockService.release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
            return;
        }
        try {
            List<String> activityIds = resolveActivityIds();
            int ok = 0;
            int fail = 0;
            for (int i = 0; i < activityIds.size(); i++) {
                String activityId = activityIds.get(i);
                // 单活动级别也抢同一把 backfill activity 锁，与可能的 backfill 写库互斥。
                String activityLockKey = JobLockKeys.productBackfillActivityLock(activityId);
                boolean acquiredActivityLock = jobLockService.tryAcquire(activityLockKey, LOCK_TTL);
                if (!acquiredActivityLock) {
                    log.info("ProductActivitySyncJob skip activity, backfill activity lock held, activityId={}", activityId);
                    continue;
                }
                try {
                    ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                            productActivitySyncApplicationService.refreshScheduledActivitySnapshots(activityId, pageSize);
                    if (result.complete()) {
                        productActivitySyncApplicationService.markActivitySyncCompleted(activityId);
                        ok++;
                    } else {
                        fail++;
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
                } catch (Exception ex) {
                    fail++;
                    log.warn("ProductActivitySyncJob activity sync failed, activityId={}", activityId, ex);
                } finally {
                    jobLockService.release(activityLockKey);
                }
                if (i < activityIds.size() - 1 && !sleepBeforeNextActivity()) {
                    break;
                }
            }
            log.info("ProductActivitySyncJob finished, ok={}, fail={}", ok, fail);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
            jobLockService.release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
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

    private int normalizedMaxActivitiesPerRun() {
        int fallback = batchSize > 0 ? batchSize : 20;
        int configured = maxActivitiesPerRun > 0 ? maxActivitiesPerRun : fallback;
        return Math.min(Math.max(configured, MIN_BATCH_SIZE), MAX_ACTIVITIES_PER_RUN);
    }

    private int normalizedBatchSize() {
        return Math.min(Math.max(batchSize, MIN_BATCH_SIZE), MAX_ACTIVITIES_PER_RUN);
    }

    private boolean sleepBeforeNextActivity() {
        if (qpsGuardSleepMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(qpsGuardSleepMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("ProductActivitySyncJob interrupted before next activity");
            return false;
        }
    }
}
