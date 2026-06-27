package com.colonel.saas.domain.product.application;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
