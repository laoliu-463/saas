package com.colonel.saas.domain.product.application;

import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.domain.product.application.port.ProductActivitySyncStatePort;
import com.colonel.saas.domain.product.policy.ActivityProductPagePolicy;
import com.colonel.saas.domain.product.policy.ProductActivityManualSyncPolicy;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品域活动商品同步应用入口。
 *
 * <p>当前保持 legacy 同步、分页、repair、事件和落库行为不变，仅先收口调用入口。</p>
 */
@Service
public class ProductActivitySyncApplicationService {

    private final ProductService productService;
    private final ProductActivitySyncStatePort productActivitySyncStatePort;

    public ProductActivitySyncApplicationService(
            ProductService productService,
            ProductActivitySyncStatePort productActivitySyncStatePort) {
        this.productService = productService;
        this.productActivitySyncStatePort = productActivitySyncStatePort;
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(DouyinProductGateway.ActivityProductQueryRequest request) {
        return ActivityProductRefreshResult.from(productService.refreshActivitySnapshots(request));
    }

    public ActivityProductRefreshResult refreshManualActivitySnapshots(
            String activityId,
            String appId,
            Integer configuredPageSize) {
        return refreshActivitySnapshots(buildManualSyncRequest(activityId, appId, configuredPageSize));
    }

    public ActivityProductRefreshResult refreshScheduledActivitySnapshots(
            String activityId,
            Integer configuredPageSize) {
        return refreshActivitySnapshots(buildScheduledSyncRequest(activityId, configuredPageSize));
    }

    public Map<String, Object> refreshActivityProductList(ActivityProductListRefreshCommand command) {
        ActivityProductRefreshResult refreshResult =
                refreshActivitySnapshots(buildActivityProductListRefreshRequest(command));
        Map<String, Object> payload = buildActivityProductListView(command.toQueryCommand());
        payload.put("syncStats", buildSyncStats(refreshResult));
        return payload;
    }

    public Map<String, Object> loadActivityProductList(ActivityProductListQueryCommand command) {
        if (productService.hasActivitySnapshots(command.activityId())) {
            return buildActivityProductListView(command);
        }
        return buildNeedSyncHint(command.activityId());
    }

    public Map<String, Object> buildManualSyncTriggerPayload(String activityId, String syncStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("syncStatus", syncStatus);
        payload.put("message", ProductActivityManualSyncPolicy.messageFor(syncStatus));
        return payload;
    }

    public void markActivitySyncCompleted(String activityId) {
        productActivitySyncStatePort.markActivitySyncCompleted(activityId, LocalDateTime.now());
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int maxPagesPerActivity,
            int maxRowsPerActivity) {
        return ActivityProductRefreshResult.from(
                productService.refreshActivitySnapshots(request, maxPagesPerActivity, maxRowsPerActivity));
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildManualSyncRequest(
            String activityId,
            String appId,
            Integer configuredPageSize) {
        Integer requestedPageSize = configuredPageSize == null || configuredPageSize <= 0
                ? ActivityProductPagePolicy.DEFAULT_PAGE_SIZE
                : configuredPageSize;
        return buildActivityProductSyncRequest(appId, activityId, requestedPageSize);
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildScheduledSyncRequest(
            String activityId,
            Integer configuredPageSize) {
        return buildActivityProductSyncRequest(null, activityId, configuredPageSize);
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildActivityProductListRefreshRequest(
            ActivityProductListRefreshCommand command) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                command.appId(),
                command.activityId(),
                command.searchType(),
                command.sortType(),
                command.count(),
                command.cooperationInfo(),
                command.cooperationType(),
                command.productInfo(),
                command.status(),
                command.retrieveMode(),
                command.cursor(),
                command.page());
    }

    private Map<String, Object> buildSyncStats(ActivityProductRefreshResult refreshResult) {
        Map<String, Object> syncStats = new LinkedHashMap<>();
        syncStats.put("syncedProductCount", refreshResult.syncedProductCount());
        syncStats.put("libraryEntryCount", refreshResult.libraryEntryCount());
        syncStats.put("createdCount", refreshResult.createdCount());
        syncStats.put("updatedCount", refreshResult.updatedCount());
        syncStats.put("skippedCount", refreshResult.skippedCount());
        syncStats.put("autoLibraryEligible", refreshResult.libraryEntryCount() > 0);
        return syncStats;
    }

    private Map<String, Object> buildActivityProductListView(ActivityProductListQueryCommand command) {
        return productService.buildActivityProductListViewFromDb(
                command.activityId(),
                command.count(),
                command.cursor(),
                command.productInfo(),
                command.bizStatus(),
                command.status(),
                command.sortBy(),
                command.goodsTags(),
                command.productTags());
    }

    private Map<String, Object> buildNeedSyncHint(String activityId) {
        Map<String, Object> hintPayload = new LinkedHashMap<>();
        hintPayload.put("items", java.util.List.of());
        hintPayload.put("total", 0L);
        hintPayload.put("activityId", activityId);
        hintPayload.put("needSync", Boolean.TRUE);
        hintPayload.put("errorCode", UpstreamErrorCode.DATA_NOT_READY.name());
        hintPayload.put("message", "该活动尚未同步商品，请先点击「同步商品」");
        hintPayload.put("lastSyncAt", null);
        return hintPayload;
    }

    private DouyinProductGateway.ActivityProductQueryRequest buildActivityProductSyncRequest(
            String appId,
            String activityId,
            Integer requestedPageSize) {
        String normalizedActivityId = activityId == null ? "" : activityId.trim();
        return new DouyinProductGateway.ActivityProductQueryRequest(
                appId,
                normalizedActivityId,
                4L,
                1L,
                ActivityProductPagePolicy.normalizePageSize(requestedPageSize),
                null,
                null,
                null,
                null,
                1L,
                null,
                null);
    }

    public record ActivityProductListRefreshCommand(
            String activityId,
            Long searchType,
            Long sortType,
            Integer count,
            String cooperationInfo,
            Integer cooperationType,
            String productInfo,
            String bizStatus,
            Integer status,
            Long retrieveMode,
            String cursor,
            Long page,
            String appId,
            String sortBy,
            String goodsTags,
            String productTags) {

        private ActivityProductListQueryCommand toQueryCommand() {
            return new ActivityProductListQueryCommand(
                    activityId,
                    count,
                    cursor,
                    productInfo,
                    bizStatus,
                    status,
                    sortBy,
                    goodsTags,
                    productTags);
        }
    }

    public record ActivityProductListQueryCommand(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer status,
            String sortBy,
            String goodsTags,
            String productTags) {
    }

    public record ActivityProductRefreshResult(
            int syncedProductCount,
            int libraryEntryCount,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int pagesFetched,
            int fetchedRows,
            int distinctProductIds,
            int duplicateProductIds,
            String stoppedReason,
            boolean stillHasNextWhenStopped,
            boolean complete) {

        public ActivityProductRefreshResult(
                int syncedProductCount,
                int libraryEntryCount,
                int createdCount,
                int updatedCount,
                int skippedCount) {
            this(syncedProductCount, libraryEntryCount, createdCount, updatedCount, skippedCount,
                    0, syncedProductCount, syncedProductCount, 0,
                    "DONE_NO_MORE", false, true);
        }

        private static ActivityProductRefreshResult from(ProductService.ActivityProductRefreshResult result) {
            return new ActivityProductRefreshResult(
                    result.syncedProductCount(),
                    result.libraryEntryCount(),
                    result.createdCount(),
                    result.updatedCount(),
                    result.skippedCount(),
                    result.pagesFetched(),
                    result.fetchedRows(),
                    result.distinctProductIds(),
                    result.duplicateProductIds(),
                    result.stoppedReason(),
                    result.stillHasNextWhenStopped(),
                    result.complete());
        }
    }
}
