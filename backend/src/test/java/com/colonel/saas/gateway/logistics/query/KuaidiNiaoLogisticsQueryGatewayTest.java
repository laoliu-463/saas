package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KuaidiNiaoLogisticsQueryGatewayTest {

    @Mock
    private LogisticsGateway delegate;

    private LogisticsProperties properties;
    private KuaidiNiaoLogisticsQueryGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        gateway = new KuaidiNiaoLogisticsQueryGateway(properties, delegate);
    }

    @Test
    void query_shouldReturnNotConfiguredWhenCredentialsMissing() {
        LogisticsQueryResult result = gateway.query("SF", "SF123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getProvider()).isEqualTo("KUAIDINIAO");
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.NOT_CONFIGURED);
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(gateway.isSupported()).isFalse();
        verify(delegate, never()).queryTrack("SF", "SF123");
    }

    @Test
    void query_shouldRejectBlankTrackingNoBeforeCallingDelegate() {
        enableKdn();

        LogisticsQueryResult result = gateway.query("SF", " ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAM");
        assertThat(result.getLogisticsCompany()).isEqualTo("SF");
        assertThat(gateway.isSupported()).isTrue();
        verify(delegate, never()).queryTrack("SF", " ");
    }

    @Test
    void query_shouldTrimInputsAndMapSuccessfulTrackWithTraceFallbacks() {
        enableKdn();
        LocalDateTime traceTime = LocalDateTime.of(2026, 5, 24, 10, 30);
        LocalDateTime signedAt = LocalDateTime.of(2026, 5, 24, 12, 0);
        when(delegate.queryTrack("AUTO", "SF123")).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "",
                "SF123",
                true,
                null,
                "3",
                "SIGNED",
                true,
                signedAt,
                List.of(new LogisticsGateway.LogisticsTraceNode(traceTime, "已签收", "深圳")),
                Map.of("success", true)));

        LogisticsQueryResult result = gateway.query(" ", " SF123 ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("KUAIDINIAO");
        assertThat(result.getLogisticsCompany()).isEqualTo("AUTO");
        assertThat(result.getTrackingNo()).isEqualTo("SF123");
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.SIGNED);
        assertThat(result.isSigned()).isTrue();
        assertThat(result.getSignedAt()).isEqualTo(signedAt);
        assertThat(result.getRawPayload()).containsEntry("success", true);
        assertThat(result.getTraces()).singleElement().satisfies(trace -> {
            assertThat(trace.getTraceTime()).isEqualTo(traceTime);
            assertThat(trace.getTraceContent()).isEqualTo("已签收");
            assertThat(trace.getLocation()).isEqualTo("深圳");
        });
    }

    @Test
    void query_shouldMapFailedAndNullUpstreamResponses() {
        enableKdn();
        when(delegate.queryTrack("SF", "EMPTY")).thenReturn(null);
        when(delegate.queryTrack("SF", "FAIL")).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "SF",
                "FAIL",
                false,
                "暂无轨迹",
                "0",
                "FAILED",
                false,
                null,
                null,
                null));

        LogisticsQueryResult empty = gateway.query("SF", "EMPTY");
        LogisticsQueryResult failed = gateway.query("SF", "FAIL");

        assertThat(empty.isSuccess()).isFalse();
        assertThat(empty.getErrorCode()).isEqualTo("UPSTREAM_FAILED");
        assertThat(empty.getErrorMessage()).isEqualTo("空响应");
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getErrorMessage()).isEqualTo("暂无轨迹");
    }

    @Test
    void query_shouldWrapDelegateExceptionAsQueryError() {
        enableKdn();
        when(delegate.queryTrack("SF", "ERR")).thenThrow(new IllegalStateException("remote timeout"));

        LogisticsQueryResult result = gateway.query("SF", "ERR");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("QUERY_ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("remote timeout");
        assertThat(result.getLogisticsCompany()).isEqualTo("SF");
    }

    @Test
    void query_shouldMapKnownInternalStatuses() {
        enableKdn();
        Map<String, LogisticsStatusCode> cases = new LinkedHashMap<>();
        cases.put("SIGNED", LogisticsStatusCode.SIGNED);
        cases.put("IN_TRANSIT", LogisticsStatusCode.IN_TRANSIT);
        cases.put("NO_TRACE", LogisticsStatusCode.IN_TRANSIT);
        cases.put("DELIVERING", LogisticsStatusCode.DELIVERING);
        cases.put("EXCEPTION", LogisticsStatusCode.REJECTED);
        cases.put("REJECTED", LogisticsStatusCode.REJECTED);
        cases.put("FAILED", LogisticsStatusCode.FAILED);
        cases.put("NOT_CONFIGURED", LogisticsStatusCode.NOT_CONFIGURED);
        cases.put("unexpected", LogisticsStatusCode.UNKNOWN);
        cases.put("", LogisticsStatusCode.UNKNOWN);

        int index = 0;
        for (Map.Entry<String, LogisticsStatusCode> entry : cases.entrySet()) {
            String trackingNo = "TRACK-" + index++;
            when(delegate.queryTrack("SF", trackingNo)).thenReturn(successTrack(entry.getKey(), trackingNo));

            LogisticsQueryResult result = gateway.query("SF", trackingNo);

            assertThat(result.getStatusCode()).isEqualTo(entry.getValue());
            assertThat(result.getStatusName()).isEqualTo(entry.getValue().name());
        }
    }

    @Test
    void query_shouldMapNullStatusAndNullRawPayloadToUnknownAndEmptyPayload() {
        enableKdn();
        when(delegate.queryTrack("SF", "NULL-STATUS")).thenReturn(successTrack(null, "NULL-STATUS"));

        LogisticsQueryResult result = gateway.query("SF", "NULL-STATUS");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.UNKNOWN);
        assertThat(result.getRawPayload()).isEmpty();
        assertThat(result.getTraces()).isEmpty();
    }

    private void enableKdn() {
        properties.getKdn().setEnabled(true);
        properties.getKdn().setEbusinessId("ebusiness");
        properties.getKdn().setApiKey("api-key");
    }

    private LogisticsGateway.LogisticsTrackResult successTrack(String internalStatus, String trackingNo) {
        return new LogisticsGateway.LogisticsTrackResult(
                "SF",
                trackingNo,
                true,
                null,
                "200",
                internalStatus,
                false,
                null,
                null,
                null);
    }
}
