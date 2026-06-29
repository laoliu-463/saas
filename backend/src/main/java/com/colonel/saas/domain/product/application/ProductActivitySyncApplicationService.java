package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.product.policy.ActivityProductPagePolicy;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import org.springframework.stereotype.Service;

/**
 * 商品域活动商品同步应用入口。
 *
 * <p>当前保持 legacy 同步、分页、repair、事件和落库行为不变，仅先收口调用入口。</p>
 */
@Service
public class ProductActivitySyncApplicationService {

    private final ProductService productService;

    public ProductActivitySyncApplicationService(ProductService productService) {
        this.productService = productService;
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
        String normalizedActivityId = activityId == null ? "" : activityId.trim();
        Integer requestedPageSize = configuredPageSize == null || configuredPageSize <= 0
                ? ActivityProductPagePolicy.DEFAULT_PAGE_SIZE
                : configuredPageSize;
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
