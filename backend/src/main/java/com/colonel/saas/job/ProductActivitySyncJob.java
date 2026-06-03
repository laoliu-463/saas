package com.colonel.saas.job;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductService;
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
    private static final int MAX_BATCH_SIZE = 20;

    private final ProductService productService;
    private final DistributedJobLockService jobLockService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final int qpsGuardSleepMillis;

    @Value("${product.activity.sync.enabled:false}")
    private boolean enabled;
    @Value("${product.activity.sync.cron:0 */5 * * * ?}")
    private String cronExpression;
    @Value("${product.activity.sync.batch-size:20}")
    private int batchSize;
    @Value("${product.activity.sync.whitelist-activities:}")
    private String whitelistActivities;

    @Autowired
    public ProductActivitySyncJob(
            ProductService productService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper) {
        this(productService, jobLockService, activityMapper, QPS_GUARD_SLEEP_MS);
    }

    ProductActivitySyncJob(
            ProductService productService,
            DistributedJobLockService jobLockService,
            ColonelsettlementActivityMapper activityMapper,
            int qpsGuardSleepMillis) {
        this.productService = productService;
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
        if (!enabled) {
            log.debug("ProductActivitySyncJob skipped (disabled by config)");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_ACTIVITY_SYNC, LOCK_TTL)) {
            log.info("ProductActivitySyncJob skipped, lock held by another node");
            return;
        }
        try {
            List<String> activityIds = resolveActivityIds();
            int ok = 0;
            int fail = 0;
            for (int i = 0; i < activityIds.size(); i++) {
                String activityId = activityIds.get(i);
                try {
                    ProductService.ActivityProductRefreshResult result =
                            productService.refreshActivitySnapshots(buildQueryRequest(activityId));
                    activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
                    ok++;
                    log.info("ProductActivitySyncJob activity synced, activityId={}, syncedProductCount={}, libraryEntryCount={}, createdCount={}, updatedCount={}, skippedCount={}",
                            activityId,
                            result.syncedProductCount(),
                            result.libraryEntryCount(),
                            result.createdCount(),
                            result.updatedCount(),
                            result.skippedCount());
                } catch (Exception ex) {
                    fail++;
                    log.warn("ProductActivitySyncJob activity sync failed, activityId={}", activityId, ex);
                }
                if (i < activityIds.size() - 1 && !sleepBeforeNextActivity()) {
                    break;
                }
            }
            log.info("ProductActivitySyncJob finished, ok={}, fail={}", ok, fail);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
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
                normalizedBatchSize(),
                LocalDateTime.now().minusMinutes(30));
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                null,
                activityId,
                4L,
                1L,
                normalizedBatchSize(),
                null,
                null,
                null,
                null,
                1L,
                null,
                null);
    }

    private int normalizedBatchSize() {
        return Math.min(Math.max(batchSize, MIN_BATCH_SIZE), MAX_BATCH_SIZE);
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
