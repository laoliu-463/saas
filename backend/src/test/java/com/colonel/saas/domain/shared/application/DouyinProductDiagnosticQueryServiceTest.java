package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;
import com.colonel.saas.domain.shared.application.port.DouyinProductDiagnosticPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinProductDiagnosticQueryServiceTest {

    private final DouyinProductDiagnosticPort port = mock(DouyinProductDiagnosticPort.class);
    private final DouyinProductDiagnosticQueryService service = new DouyinProductDiagnosticQueryService(port);

    @Test
    void activityProducts_shouldDelegateToPort() {
        DouyinActivityProductProbeQuery query =
                new DouyinActivityProductProbeQuery("app-1", "ACT-1", 20, "cursor-1");
        Map<String, Object> remoteResponse = Map.of("total", 1L);
        when(port.activityProducts(query)).thenReturn(remoteResponse);

        assertThat(service.activityProducts(query)).isEqualTo(remoteResponse);

        verify(port).activityProducts(query);
    }

    @Test
    void productSkus_shouldDelegateToPort() {
        List<DouyinProductSkuView> skus = List.of(
                new DouyinProductSkuView("sku-1", "red", 100L, 2, "cover-url"));
        when(port.productSkus("P-1")).thenReturn(skus);

        assertThat(service.productSkus("P-1")).isEqualTo(skus);

        verify(port).productSkus("P-1");
    }
}
