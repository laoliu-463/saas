package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeResult;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenProbeResponseView;
import com.colonel.saas.domain.shared.application.port.DouyinTokenDiagnosticPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DouyinTokenDiagnosticQueryServiceTest {

    @Test
    void institutionInfo_shouldReturnPortResponseUnchanged() {
        Map<String, Object> expected = Map.of("code", 0, "data", Map.of("institutionId", "I-1"));
        DouyinTokenDiagnosticQueryService service = new DouyinTokenDiagnosticQueryService(
                new StubPort(expected, null));

        Map<String, Object> actual = service.institutionInfo("app-1");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void probeCreateToken_shouldReturnPortResponseUnchanged() {
        DouyinTokenCreateProbeResult expected = new DouyinTokenCreateProbeResult(
                "authorization_code",
                "present",
                "shop",
                "S1",
                true,
                "MERCHANT",
                new DouyinTokenProbeResponseView("0", "ok", null, null, "acc***", "ref***", 7200L, "AUTH", "MERCHANT", 0L));
        DouyinTokenDiagnosticQueryService service = new DouyinTokenDiagnosticQueryService(
                new StubPort(Map.of(), expected));

        DouyinTokenCreateProbeResult actual = service.probeCreateToken(
                new DouyinTokenCreateProbeCommand("code", "authorization_code", "shop", "S1", "AUTH", "MERCHANT"));

        assertThat(actual).isSameAs(expected);
    }

    private record StubPort(
            Map<String, Object> institutionInfo,
            DouyinTokenCreateProbeResult probeResult)
            implements DouyinTokenDiagnosticPort {

        @Override
        public Map<String, Object> institutionInfo(String appId) {
            return institutionInfo;
        }

        @Override
        public DouyinTokenCreateProbeResult probeCreateToken(DouyinTokenCreateProbeCommand command) {
            return probeResult;
        }
    }
}
