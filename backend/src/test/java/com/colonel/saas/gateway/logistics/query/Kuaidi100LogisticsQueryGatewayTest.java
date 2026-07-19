package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class Kuaidi100LogisticsQueryGatewayTest {

    @Mock
    private Kuaidi100LogisticsGateway delegate;
    @Mock
    private ObjectProvider<Kuaidi100LogisticsGateway> delegateProvider;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private LogisticsProperties properties;
    private Kuaidi100LogisticsQueryGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        lenient().when(delegateProvider.getIfAvailable()).thenReturn(delegate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(31))))
                .thenReturn(true);
        gateway = new Kuaidi100LogisticsQueryGateway(properties, delegateProvider, redisTemplate);
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
    void query_shouldReturnNotConfiguredWhenConcreteKuaidi100GatewayMissing() {
        enableKd100();
        when(delegateProvider.getIfAvailable()).thenReturn(null);

        LogisticsQueryResult result = gateway.query("YTO", "YT123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getProvider()).isEqualTo("KUAIDI100");
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.NOT_CONFIGURED);
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(gateway.isSupported()).isFalse();
        verify(delegate, never()).queryTrack(any(LogisticsTrackCommand.class));
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
    void query_shouldRejectMissingCompanyBeforeCallingDelegate() {
        enableKd100();

        LogisticsQueryResult result = gateway.query(" ", "SF123");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAM");
        assertThat(result.getErrorMessage()).contains("快递公司编码");
        verify(delegate, never()).queryTrack("AUTO", "SF123");
    }

    @Test
    void query_shouldTrimInputsAndMapSuccessfulTrackWithTraceFallbacks() {
        enableKd100();
        LocalDateTime traceTime = LocalDateTime.of(2026, 5, 24, 10, 30);
        LocalDateTime signedAt = LocalDateTime.of(2026, 5, 24, 12, 0);
        when(delegate.queryTrack(command("YTO", "YT123"))).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "YTO",
                "YT123",
                true,
                null,
                "F00",
                "SIGNED",
                true,
                signedAt,
                List.of(new LogisticsGateway.LogisticsTraceNode(traceTime, "已签收", "深圳")),
                Map.of("status", "200")));

        LogisticsQueryResult result = gateway.query(" YTO ", " YT123 ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("KUAIDI100");
        assertThat(result.getLogisticsCompany()).isEqualTo("YTO");
        assertThat(result.getTrackingNo()).isEqualTo("YT123");
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
    void query_shouldPassPhoneAndAddressToDelegateCommand() {
        enableKd100();
        when(delegate.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123")
                .phone("13800138000")
                .to("广东省深圳市南山区")
                .resultV2("4")
                .build())).thenReturn(successTrack("IN_TRANSIT", "SF123"));

        LogisticsQueryResult result = gateway.query(LogisticsTrackCommand.builder()
                .companyCode(" SF ")
                .trackingNo(" SF123 ")
                .phone("13800138000")
                .to("广东省深圳市南山区")
                .build());

        assertThat(result.isSuccess()).isTrue();
        verify(delegate).queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123")
                .phone("13800138000")
                .to("广东省深圳市南山区")
                .resultV2("4")
                .build());
    }

    @Test
    void query_shouldThrottleRepeatedTrackingNoBeforeCallingDelegateAgain() {
        enableKd100();
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(31))))
                .thenReturn(true, false);
        when(delegate.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .resultV2("4")
                .build())).thenReturn(successTrack("IN_TRANSIT", "YT001"));

        LogisticsQueryResult first = gateway.query("YTO", "YT001");
        LogisticsQueryResult second = gateway.query("YTO", "YT001");

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getErrorCode()).isEqualTo("QUERY_THROTTLED");
        assertThat(second.getErrorMessage()).contains("31分钟");
        verify(valueOperations, org.mockito.Mockito.times(2)).setIfAbsent(
                org.mockito.ArgumentMatchers.startsWith("logistics:kuaidi100:query-throttle:"),
                anyString(),
                eq(Duration.ofMinutes(31)));
        verify(delegate).queryTrack(LogisticsTrackCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .resultV2("4")
                .build());
    }

    @Test
    void query_shouldFailClosedWhenRedisThrottleStateUnavailable() {
        enableKd100();
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(31))))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        LogisticsQueryResult result = gateway.query("YTO", "YT-REDIS-DOWN");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("THROTTLE_STATE_UNAVAILABLE");
        verify(delegate, never()).queryTrack(any(LogisticsTrackCommand.class));
    }

    @Test
    void query_shouldRejectPhoneRequiredCompanyBeforeThrottleAndAllowCorrectedRequest() {
        enableKd100();

        LogisticsQueryResult missingPhone = gateway.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF001")
                .build());

        assertThat(missingPhone.isSuccess()).isFalse();
        assertThat(missingPhone.getErrorCode()).isEqualTo("INVALID_PARAM");
        assertThat(missingPhone.getErrorMessage()).contains("手机号");
        verify(delegate, never()).queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF001")
                .resultV2("4")
                .build());

        when(delegate.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF001")
                .phone("13800138000")
                .resultV2("4")
                .build())).thenReturn(successTrack("SF", "IN_TRANSIT", "SF001"));

        LogisticsQueryResult corrected = gateway.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF001")
                .phone("13800138000")
                .build());

        assertThat(corrected.isSuccess()).isTrue();
        verify(delegate).queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF001")
                .phone("13800138000")
                .resultV2("4")
                .build());
    }

    @Test
    void query_shouldMapFailedAndNullUpstreamResponses() {
        enableKd100();
        when(delegate.queryTrack(command("YTO", "EMPTY"))).thenReturn(null);
        when(delegate.queryTrack(command("YTO", "FAIL"))).thenReturn(new LogisticsGateway.LogisticsTrackResult(
                "YTO",
                "FAIL",
                false,
                "暂无轨迹",
                "400",
                "FAILED",
                false,
                null,
                null,
                null));

        LogisticsQueryResult empty = gateway.query("YTO", "EMPTY");
        LogisticsQueryResult failed = gateway.query("YTO", "FAIL");

        assertThat(empty.isSuccess()).isFalse();
        assertThat(empty.getErrorCode()).isEqualTo("UPSTREAM_FAILED");
        assertThat(empty.getErrorMessage()).isEqualTo("空响应");
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getErrorMessage()).isEqualTo("暂无轨迹");
    }

    @Test
    void query_shouldWrapDelegateExceptionAsQueryError() {
        enableKd100();
        when(delegate.queryTrack(command("YTO", "ERR"))).thenThrow(new IllegalStateException("remote timeout"));

        LogisticsQueryResult result = gateway.query("YTO", "ERR");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("QUERY_ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("remote timeout");
        assertThat(result.getLogisticsCompany()).isEqualTo("YTO");
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
            when(delegate.queryTrack(command("YTO", trackingNo))).thenReturn(successTrack("YTO", entry.getKey(), trackingNo));

            LogisticsQueryResult result = gateway.query("YTO", trackingNo);

            assertThat(result.getStatusCode()).isEqualTo(entry.getValue());
            assertThat(result.getStatusName()).isEqualTo(entry.getValue().name());
        }
    }

    @Test
    void query_shouldMapNullStatusAndNullRawPayloadToUnknownAndEmptyPayload() {
        enableKd100();
        when(delegate.queryTrack(command("YTO", "NULL-STATUS"))).thenReturn(successTrack("YTO", null, "NULL-STATUS"));

        LogisticsQueryResult result = gateway.query("YTO", "NULL-STATUS");

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
        return successTrack("SF", internalStatus, trackingNo);
    }

    private LogisticsGateway.LogisticsTrackResult successTrack(String companyCode, String internalStatus, String trackingNo) {
        return new LogisticsGateway.LogisticsTrackResult(
                companyCode,
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

    private LogisticsTrackCommand command(String companyCode, String trackingNo) {
        return LogisticsTrackCommand.builder()
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .resultV2("4")
                .build();
    }
}
