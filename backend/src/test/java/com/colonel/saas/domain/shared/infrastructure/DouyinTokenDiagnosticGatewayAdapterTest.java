package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinTokenDiagnosticGatewayAdapterTest {

    @Test
    void institutionInfo_shouldDelegateToGateway() {
        DouyinTokenGateway gateway = mock(DouyinTokenGateway.class);
        Map<String, Object> expected = Map.of("code", 0);
        when(gateway.institutionInfo("app-1")).thenReturn(expected);
        DouyinTokenDiagnosticGatewayAdapter adapter = new DouyinTokenDiagnosticGatewayAdapter(gateway);

        Map<String, Object> actual = adapter.institutionInfo("app-1");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void probeCreateToken_shouldMapCommandAndResult() {
        DouyinTokenGateway gateway = mock(DouyinTokenGateway.class);
        when(gateway.probeCreateToken(any())).thenReturn(new DouyinTokenGateway.ProbeTokenCreateResult(
                "authorization_code",
                "present",
                "shop",
                "S1",
                true,
                "MERCHANT",
                new DouyinTokenGateway.TokenProbeResponseView(
                        "0", "ok", null, null, "acc***", "ref***", 7200L, "AUTH", "MERCHANT", 0L)));
        DouyinTokenDiagnosticGatewayAdapter adapter = new DouyinTokenDiagnosticGatewayAdapter(gateway);

        var result = adapter.probeCreateToken(
                new DouyinTokenCreateProbeCommand("code", "authorization_code", "shop", "S1", "AUTH", "MERCHANT"));

        assertThat(result.grantType()).isEqualTo("authorization_code");
        assertThat(result.response().maskedAccessToken()).isEqualTo("acc***");
        verify(gateway).probeCreateToken(new DouyinTokenGateway.TokenCreateCommand(
                "code", "authorization_code", "shop", "S1", "AUTH", "MERCHANT"));
    }

    @Test
    void institutionInfo_shouldPropagateGatewayException() {
        DouyinTokenGateway gateway = mock(DouyinTokenGateway.class);
        when(gateway.institutionInfo("app-1")).thenThrow(new IllegalStateException("upstream down"));
        DouyinTokenDiagnosticGatewayAdapter adapter = new DouyinTokenDiagnosticGatewayAdapter(gateway);

        assertThatThrownBy(() -> adapter.institutionInfo("app-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("upstream down");
    }
}
