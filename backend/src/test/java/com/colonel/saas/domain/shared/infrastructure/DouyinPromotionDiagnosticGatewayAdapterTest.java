package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinPromotionDiagnosticGatewayAdapterTest {

    private final DouyinPromotionGateway gateway = mock(DouyinPromotionGateway.class);
    private final DouyinPromotionDiagnosticGatewayAdapter adapter =
            new DouyinPromotionDiagnosticGatewayAdapter(gateway);

    @Test
    void rawUpstreamPost_shouldDelegateToPromotionGateway() {
        Map<String, Object> payload = Map.of("product_url", "https://item.example/1");
        Map<String, Object> remoteResponse = Map.of("code", 0);
        when(gateway.rawUpstreamPost("app-1", "buyin.instPickSourceConvert", payload))
                .thenReturn(remoteResponse);

        Map<String, Object> result = adapter.rawUpstreamPost(
                new DouyinPromotionRawProbeCommand("app-1", "buyin.instPickSourceConvert", payload));

        assertThat(result).isEqualTo(remoteResponse);
        verify(gateway).rawUpstreamPost("app-1", "buyin.instPickSourceConvert", payload);
    }
}
