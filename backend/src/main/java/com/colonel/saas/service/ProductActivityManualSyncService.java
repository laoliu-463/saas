package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
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

    private final ProductService productService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final Executor syncExecutor;
    private final Set<String> runningActivityIds = ConcurrentHashMap.newKeySet();

    public ProductActivityManualSyncService(
            ProductService productService,
            ColonelsettlementActivityService colonelActivityService,
            ColonelsettlementActivityMapper activityMapper,
            @Qualifier("applicationTaskExecutor") Executor syncExecutor) {
        this.productService = productService;
        this.colonelActivityService = colonelActivityService;
        this.activityMapper = activityMapper;
        this.syncExecutor = syncExecutor;
    }

    public SyncTriggerResult trigger(String activityId, String appId) {
        String normalizedActivityId = activityId == null ? "" : activityId.trim();
        if (!StringUtils.hasText(normalizedActivityId)) {
            return new SyncTriggerResult("", "INVALID");
        }
        if (!runningActivityIds.add(normalizedActivityId)) {
            return new SyncTriggerResult(normalizedActivityId, "RUNNING");
        }
        try {
            CompletableFuture.runAsync(() -> runSync(normalizedActivityId, appId), syncExecutor);
        } catch (RuntimeException ex) {
            runningActivityIds.remove(normalizedActivityId);
            throw ex;
        }
        return new SyncTriggerResult(normalizedActivityId, "ACCEPTED");
    }

    private void runSync(String activityId, String appId) {
        try {
            colonelActivityService.syncActivitySummaryFromUpstream(activityId, appId);
            ProductService.ActivityProductRefreshResult result =
                    productService.refreshActivitySnapshots(buildQueryRequest(activityId, appId));
            activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
            log.info(
                    "ProductActivityManualSync completed, activityId={}, syncedProductCount={}, libraryEntryCount={}, createdCount={}, updatedCount={}, skippedCount={}",
                    activityId,
                    result.syncedProductCount(),
                    result.libraryEntryCount(),
                    result.createdCount(),
                    result.updatedCount(),
                    result.skippedCount());
        } catch (Exception ex) {
            log.warn("ProductActivityManualSync failed, activityId={}", activityId, ex);
        } finally {
            runningActivityIds.remove(activityId);
        }
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildQueryRequest(String activityId, String appId) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                appId,
                activityId,
                4L,
                1L,
                DEFAULT_PAGE_SIZE,
                null,
                null,
                null,
                null,
                1L,
                null,
                null);
    }

    public record SyncTriggerResult(String activityId, String syncStatus) {
    }
}
