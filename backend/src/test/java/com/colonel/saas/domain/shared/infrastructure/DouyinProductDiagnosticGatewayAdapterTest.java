package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinProductDiagnosticGatewayAdapterTest {

    private final DouyinProductGateway gateway = mock(DouyinProductGateway.class);
    private final DouyinProductDiagnosticGatewayAdapter adapter =
            new DouyinProductDiagnosticGatewayAdapter(gateway);

    @Test
    void activityProducts_shouldMapProbeQueryToGatewayRequestAndReturnLegacyMap() {
        DouyinProductGateway.ActivityProductItem item = new DouyinProductGateway.ActivityProductItem(
                1001L,
                "product",
                "cover",
                100L,
                "1.00",
                1000L,
                10L,
                1200L,
                "12%",
                1,
                "普通",
                "0",
                0L,
                true,
                true,
                10L,
                2001L,
                "shop",
                "95",
                1,
                "online",
                "cat",
                "10",
                null,
                "2026-07-01",
                "2026-07-02",
                "2026-07-01",
                "2026-07-02",
                "detail",
                null,
                Map.of("rawKey", "rawValue"));
        when(gateway.queryActivityProducts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 123L, 456L, 1L, "next-1", List.of(item)));

        Map<String, Object> result = adapter.activityProducts(
                new DouyinActivityProductProbeQuery("app-1", "ACT-1", 20, "cursor-1"));

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(gateway).queryActivityProducts(captor.capture());
        DouyinProductGateway.ActivityProductQueryRequest request = captor.getValue();
        assertThat(request.appId()).isEqualTo("app-1");
        assertThat(request.activityId()).isEqualTo("ACT-1");
        assertThat(request.searchType()).isEqualTo(4L);
        assertThat(request.sortType()).isEqualTo(1L);
        assertThat(request.count()).isEqualTo(20);
        assertThat(request.retrieveMode()).isEqualTo(1L);
        assertThat(request.cursor()).isEqualTo("cursor-1");
        assertThat(result)
                .containsEntry("test", false)
                .containsEntry("activityId", 123L)
                .containsEntry("institutionId", 456L)
                .containsEntry("total", 1L)
                .containsEntry("nextCursor", "next-1");
        assertThat((List<?>) result.get("items")).hasSize(1);
    }

    @Test
    void productSkus_shouldMapGatewaySkuResultToDiagnosticView() {
        when(gateway.queryProductSkus("P-1")).thenReturn(List.of(
                new DouyinProductGateway.ProductSkuResult("sku-1", "red", 100L, 2, "cover-url")));

        List<DouyinProductSkuView> result = adapter.productSkus("P-1");

        assertThat(result).containsExactly(new DouyinProductSkuView("sku-1", "red", 100L, 2, "cover-url"));
        verify(gateway).queryProductSkus("P-1");
    }
}
