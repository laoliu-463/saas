package com.colonel.saas.domain.product.application;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivitySyncApplicationServiceTest {

    @Mock
    private ProductService productService;

    @Test
    void refreshActivitySnapshots_shouldDelegateToLegacyProductServiceAndMapStats() {
        ProductActivitySyncApplicationService applicationService =
                new ProductActivitySyncApplicationService(productService);
        DouyinProductGateway.ActivityProductQueryRequest request =
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, "ACT-1", 4L, 1L, 20, null, null, null, null, 1L, null, null);
        when(productService.refreshActivitySnapshots(request, 7, 900))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        11,
                        2,
                        3,
                        4,
                        1,
                        5,
                        12,
                        11,
                        1,
                        "MAX_PAGES_REACHED",
                        true,
                        false));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshActivitySnapshots(request, 7, 900);

        assertThat(result.syncedProductCount()).isEqualTo(11);
        assertThat(result.libraryEntryCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(4);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.pagesFetched()).isEqualTo(5);
        assertThat(result.fetchedRows()).isEqualTo(12);
        assertThat(result.distinctProductIds()).isEqualTo(11);
        assertThat(result.duplicateProductIds()).isEqualTo(1);
        assertThat(result.stoppedReason()).isEqualTo("MAX_PAGES_REACHED");
        assertThat(result.stillHasNextWhenStopped()).isTrue();
        assertThat(result.complete()).isFalse();
        verify(productService).refreshActivitySnapshots(request, 7, 900);
    }

    @Test
    void refreshManualActivitySnapshots_shouldBuildClampedRequestAndDelegate() {
        ProductActivitySyncApplicationService applicationService =
                new ProductActivitySyncApplicationService(productService);
        when(productService.refreshActivitySnapshots(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        9,
                        3,
                        4,
                        5,
                        0,
                        2,
                        9,
                        8,
                        1,
                        "DONE_NO_MORE",
                        false,
                        true));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshManualActivitySnapshots(" ACT-1 ", "APP-1", 100);

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().appId()).isEqualTo("APP-1");
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-1");
        assertThat(captor.getValue().searchType()).isEqualTo(4L);
        assertThat(captor.getValue().sortType()).isEqualTo(1L);
        assertThat(captor.getValue().count()).isEqualTo(20);
        assertThat(captor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(captor.getValue().page()).isNull();
        assertThat(result.libraryEntryCount()).isEqualTo(3);
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void refreshScheduledActivitySnapshots_shouldBuildLegacyScheduledRequestAndDelegate() {
        ProductActivitySyncApplicationService applicationService =
                new ProductActivitySyncApplicationService(productService);
        when(productService.refreshActivitySnapshots(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(7, 2, 3, 4, 0));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshScheduledActivitySnapshots(" ACT-9 ", 0);

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().appId()).isNull();
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-9");
        assertThat(captor.getValue().searchType()).isEqualTo(4L);
        assertThat(captor.getValue().sortType()).isEqualTo(1L);
        assertThat(captor.getValue().count()).isEqualTo(1);
        assertThat(captor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(captor.getValue().page()).isNull();
        assertThat(result.syncedProductCount()).isEqualTo(7);
        assertThat(result.libraryEntryCount()).isEqualTo(2);
    }
}
