package com.colonel.saas.service;

import com.colonel.saas.domain.product.application.ProductActivitySyncApplicationService;
import com.colonel.saas.domain.product.policy.ProductActivityManualSyncPolicy;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    private final ProductActivitySyncApplicationService productActivitySyncApplicationService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final ColonelsettlementActivityMapper activityMapper;
    private final Executor syncExecutor;
    private final Set<String> runningActivityIds = ConcurrentHashMap.newKeySet();
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int pageSize = 20;

    public ProductActivityManualSyncService(
            ProductActivitySyncApplicationService productActivitySyncApplicationService,
            ColonelsettlementActivityService colonelActivityService,
            ColonelsettlementActivityMapper activityMapper,
            @Qualifier("applicationTaskExecutor") Executor syncExecutor) {
        this.productActivitySyncApplicationService = productActivitySyncApplicationService;
        this.colonelActivityService = colonelActivityService;
        this.activityMapper = activityMapper;
        this.syncExecutor = syncExecutor;
    }

    public SyncTriggerResult trigger(String activityId, String appId) {
        String normalizedActivityId = activityId == null ? "" : activityId.trim();
        if (!StringUtils.hasText(normalizedActivityId)) {
            return new SyncTriggerResult("", ProductActivityManualSyncPolicy.STATUS_INVALID);
        }
        if (!runningActivityIds.add(normalizedActivityId)) {
            return new SyncTriggerResult(normalizedActivityId, ProductActivityManualSyncPolicy.STATUS_RUNNING);
        }
        try {
            CompletableFuture.runAsync(() -> runSync(normalizedActivityId, appId), syncExecutor);
        } catch (RuntimeException ex) {
            runningActivityIds.remove(normalizedActivityId);
            throw ex;
        }
        return new SyncTriggerResult(normalizedActivityId, ProductActivityManualSyncPolicy.STATUS_ACCEPTED);
    }

    private void runSync(String activityId, String appId) {
        try {
            colonelActivityService.syncActivitySummaryFromUpstream(activityId, appId);
            ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                    productActivitySyncApplicationService.refreshManualActivitySnapshots(activityId, appId, pageSize);
            if (result.complete()) {
                activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
            }
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
            log.warn("ProductActivityManualSync failed, activityId={}", activityId, ex);
        } finally {
            runningActivityIds.remove(activityId);
        }
    }

    public record SyncTriggerResult(String activityId, String syncStatus) {
    }
}
