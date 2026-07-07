package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinOrderDiagnosticGatewayAdapterTest {

    private final DouyinOrderGateway gateway = mock(DouyinOrderGateway.class);
    private final DouyinOrderDiagnosticGatewayAdapter adapter =
            new DouyinOrderDiagnosticGatewayAdapter(gateway);

    @Test
    void instituteOrdersRawResponse_shouldMapQueryToGatewayRequestAndReturnRawResponse() {
        Map<String, Object> remoteResponse = Map.of("data", Map.of("orders", List.of(Map.of("order_id", "O-1"))));
        when(gateway.listInstituteOrders(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", remoteResponse));

        Map<String, Object> result = adapter.instituteOrdersRawResponse(
                new DouyinOrderRawProbeQuery(1711900800L, 1711987200L, 50, "cursor-1"));

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(gateway).listInstituteOrders(captor.capture());
        DouyinOrderGateway.DouyinOrderQueryRequest request = captor.getValue();
        assertThat(request.startTime()).isEqualTo(1711900800L);
        assertThat(request.endTime()).isEqualTo(1711987200L);
        assertThat(request.count()).isEqualTo(50);
        assertThat(request.cursor()).isEqualTo("cursor-1");
        assertThat(request.resolvedTimeType()).isEqualTo("update");
        assertThat(result).isEqualTo(remoteResponse);
    }
}
