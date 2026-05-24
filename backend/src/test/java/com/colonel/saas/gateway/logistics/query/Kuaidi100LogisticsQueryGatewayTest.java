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
class Kuaidi100LogisticsQueryGatewayTest {

    @Mock
    private LogisticsGateway delegate;

    private LogisticsProperties properties;
    private Kuaidi100LogisticsQueryGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        gateway = new Kuaidi100LogisticsQueryGateway(properties, delegate);
    }

    @Test
    void query_shouldReturnNotConfiguredWhenCredentialsMissing() {
        LogisticsQueryResult result = gateway.query("SF", "SF123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getProvider()).isEqualTo("KUAIDI100");
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.NOT_CONFIGURED);
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(gateway.isSupported()).isFalse();
        verify(delegate, never()).queryTrack("SF", "SF123");
    }

    @Test
    void query_shouldRejectBlankTrackingNoBeforeCallingDelegate() {
        enableKd100();

        LogisticsQueryResult result = gateway.query("SF", " ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAM");
        assertThat(result.getLogisticsCompany()).isEqualTo("SF");
        assertThat(gateway.isSupported()).isTrue();
        verify(delegate, never()).queryTrack("SF", " ");
    }

    @Test
    void query_shouldTrimInputsAndMapSuccessfulTrackWithTraceFallbacks() {
        enableKd100();
        LocalDateTime traceTime = LocalDateTime.of(2026, 5, 24, 10, 30);
        LocalDateTime signedAt = LocalDateTime.of(2026, 5, 24, 12, 0);
        when(delegate.queryTrack("AUTO", "SF123")).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "",
                "SF123",
                true,
                null,
                "F00",
                "SIGNED",
                true,
                signedAt,
                List.of(new LogisticsGateway.LogisticsTraceNode(traceTime, "已签收", "深圳")),
                Map.of("status", "200")));

        LogisticsQueryResult result = gateway.query(" ", " SF123 ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("KUAIDI100");
        assertThat(result.getLogisticsCompany()).isEqualTo("AUTO");
        assertThat(result.getTrackingNo()).isEqualTo("SF123");
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.SIGNED);
        assertThat(result.isSigned()).isTrue();
        assertThat(result.getSignedAt()).isEqualTo(signedAt);
        assertThat(result.getRawPayload()).containsEntry("status", "200");
        assertThat(result.getTraces()).singleElement().satisfies(trace -> {
            assertThat(trace.getTraceTime()).isEqualTo(traceTime);
            assertThat(trace.getTraceContent()).isEqualTo("已签收");
            assertThat(trace.getLocation()).isEqualTo("深圳");
        });
    }

    @Test
    void query_shouldMapFailedAndNullUpstreamResponses() {
        enableKd100();
        when(delegate.queryTrack("SF", "EMPTY")).thenReturn(null);
        when(delegate.queryTrack("SF", "FAIL")).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "SF",
                "FAIL",
                false,
                "暂无轨迹",
                "400",
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
        enableKd100();
        when(delegate.queryTrack("SF", "ERR")).thenThrow(new IllegalStateException("remote timeout"));

        LogisticsQueryResult result = gateway.query("SF", "ERR");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("QUERY_ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("remote timeout");
        assertThat(result.getLogisticsCompany()).isEqualTo("SF");
    }

    @Test
    void query_shouldMapKnownInternalStatuses() {
        enableKd100();
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
        enableKd100();
        when(delegate.queryTrack("SF", "NULL-STATUS")).thenReturn(successTrack(null, "NULL-STATUS"));

        LogisticsQueryResult result = gateway.query("SF", "NULL-STATUS");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.UNKNOWN);
        assertThat(result.getRawPayload()).isEmpty();
        assertThat(result.getTraces()).isEmpty();
    }

    private void enableKd100() {
        properties.getKd100().setEnabled(true);
        properties.getKd100().setCustomer("customer");
        properties.getKd100().setKey("key");
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
