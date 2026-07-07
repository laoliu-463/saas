package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;
import com.colonel.saas.domain.shared.application.port.DouyinOrderDiagnosticPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinOrderDiagnosticQueryServiceTest {

    private final DouyinOrderDiagnosticPort port = mock(DouyinOrderDiagnosticPort.class);
    private final DouyinOrderDiagnosticQueryService service = new DouyinOrderDiagnosticQueryService(port);

    @Test
    void instituteOrdersRawResponse_shouldDelegateToPort() {
        DouyinOrderRawProbeQuery query = new DouyinOrderRawProbeQuery(1711900800L, 1711987200L, 20, "cursor-1");
        Map<String, Object> remoteResponse = Map.of("code", 0);
        when(port.instituteOrdersRawResponse(query)).thenReturn(remoteResponse);

        assertThat(service.instituteOrdersRawResponse(query)).isEqualTo(remoteResponse);

        verify(port).instituteOrdersRawResponse(query);
    }
}
