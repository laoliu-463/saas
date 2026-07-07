package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinPromotionDiagnosticPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinPromotionDiagnosticQueryServiceTest {

    private final DouyinPromotionDiagnosticPort port = mock(DouyinPromotionDiagnosticPort.class);
    private final DouyinPromotionDiagnosticQueryService service = new DouyinPromotionDiagnosticQueryService(port);

    @Test
    void rawUpstreamPost_shouldDelegateToPort() {
        DouyinPromotionRawProbeCommand command = new DouyinPromotionRawProbeCommand(
                "app-1",
                "buyin.instPickSourceConvert",
                Map.of("product_url", "https://item.example/1"));
        Map<String, Object> remoteResponse = Map.of("code", 0);
        when(port.rawUpstreamPost(command)).thenReturn(remoteResponse);

        assertThat(service.rawUpstreamPost(command)).isEqualTo(remoteResponse);

        verify(port).rawUpstreamPost(command);
    }
}
