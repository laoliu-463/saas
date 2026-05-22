package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsTrackServiceTest {

    @Mock
    private LogisticsGateway logisticsGateway;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;

    private LogisticsTrackService service;

    @BeforeEach
    void setUp() {
        service = new LogisticsTrackService(logisticsGateway, sampleRequestMapper, sampleStatusLogService);
        lenient().when(sampleRequestMapper.updateById(any(SampleRequest.class))).thenReturn(1);
    }

    private SampleRequest shippingSample(String trackingNo, String shipperCode) {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SR-001");
        sample.setStatus(3); // SHIPPING
        sample.setTrackingNo(trackingNo);
        sample.setShipperCode(shipperCode);
        sample.setUserId(UUID.randomUUID());
        return sample;
    }

    private LogisticsGateway.LogisticsTrackResult signedResult(LocalDateTime signedAt) {
        return new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-SIGNED-001", true, null, "3", "SIGNED",
                true, signedAt,
                List.of(new LogisticsGateway.LogisticsTraceNode(signedAt, "已签收", "签收")),
                Map.of()
        );
    }

    @Test
    @DisplayName("SIGNED 状态应推进到 PENDING_HOMEWORK")
    void refreshAndProgress_signedStatus_progressesToPendingHomework() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", "SF");
        LocalDateTime signedAt = LocalDateTime.of(2026, 5, 14, 10, 30);
        when(logisticsGateway.queryTrack("SF", "TEST-SIGNED-001")).thenReturn(signedResult(signedAt));

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(5); // PENDING_HOMEWORK
        assertThat(sample.getDeliverTime()).isEqualTo(signedAt);
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(eq(sample.getId()), eq(3), eq(5), any(UUID.class), eq("物流签收自动推进"));
    }

    @Test
    @DisplayName("SIGNED 且 signedAt 为空时使用当前时间")
    void refreshAndProgress_signedWithNullSignedAt_usesNow() {
        SampleRequest sample = shippingSample("TEST-SIGNED-002", "SF");
        LogisticsGateway.LogisticsTrackResult result = new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-SIGNED-002", true, null, "3", "SIGNED",
                true, null, List.of(), Map.of()
        );
        when(logisticsGateway.queryTrack("SF", "TEST-SIGNED-002")).thenReturn(result);

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(5);
        assertThat(sample.getDeliverTime()).isNotNull();
    }

    @Test
    @DisplayName("FAILED 状态不应推进")
    void refreshAndProgress_failedStatus_noProgress() {
        SampleRequest sample = shippingSample("TEST-FAILED-001", "SF");
        LogisticsGateway.LogisticsTrackResult result = new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-FAILED-001", false, "模拟查询失败", null, "FAILED",
                false, null, List.of(), Map.of()
        );
        when(logisticsGateway.queryTrack("SF", "TEST-FAILED-001")).thenReturn(result);

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(3);
        verify(sampleRequestMapper, never()).updateById(any());
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("NO_TRACE 状态不应推进")
    void refreshAndProgress_noTraceStatus_noProgress() {
        SampleRequest sample = shippingSample("TEST-NO_TRACE-001", "SF");
        LogisticsGateway.LogisticsTrackResult result = new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-NO_TRACE-001", true, null, null, "NO_TRACE",
                false, null, List.of(), Map.of()
        );
        when(logisticsGateway.queryTrack("SF", "TEST-NO_TRACE-001")).thenReturn(result);

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(3);
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("EXCEPTION 状态不应推进")
    void refreshAndProgress_exceptionStatus_noProgress() {
        SampleRequest sample = shippingSample("TEST-EXCEPTION-001", "SF");
        LogisticsGateway.LogisticsTrackResult result = new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-EXCEPTION-001", true, null, "4", "EXCEPTION",
                false, null, List.of(), Map.of()
        );
        when(logisticsGateway.queryTrack("SF", "TEST-EXCEPTION-001")).thenReturn(result);

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(3);
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("IN_TRANSIT 状态不应推进")
    void refreshAndProgress_inTransitStatus_noProgress() {
        SampleRequest sample = shippingSample("TEST-IN_TRANSIT-001", "SF");
        LogisticsGateway.LogisticsTrackResult result = new LogisticsGateway.LogisticsTrackResult(
                "SF", "TEST-IN_TRANSIT-001", true, null, "2", "IN_TRANSIT",
                false, null,
                List.of(new LogisticsGateway.LogisticsTraceNode(LocalDateTime.now(), "运输中", "")),
                Map.of()
        );
        when(logisticsGateway.queryTrack("SF", "TEST-IN_TRANSIT-001")).thenReturn(result);

        service.refreshAndProgress(sample);

        assertThat(sample.getStatus()).isEqualTo(3);
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("已处于 PENDING_HOMEWORK 时幂等跳过")
    void refreshAndProgress_alreadyPendingHomework_idempotentSkip() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", "SF");
        sample.setStatus(5); // PENDING_HOMEWORK

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("已处于 COMPLETED 时幂等跳过")
    void refreshAndProgress_alreadyCompleted_idempotentSkip() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", "SF");
        sample.setStatus(6); // COMPLETED

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
    }

    @Test
    @DisplayName("缺少 trackingNo 时跳过")
    void refreshAndProgress_missingTrackingNo_skip() {
        SampleRequest sample = shippingSample(null, "SF");

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("缺少 shipperCode 时跳过")
    void refreshAndProgress_missingShipperCode_skip() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", null);

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
    }

    @Test
    @DisplayName("trackingNo 为空字符串时跳过")
    void refreshAndProgress_emptyTrackingNo_skip() {
        SampleRequest sample = shippingSample("", "SF");

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
    }

    @Test
    @DisplayName("网关异常时不推进状态")
    void refreshAndProgress_gatewayException_noProgress() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", "SF");
        when(logisticsGateway.queryTrack("SF", "TEST-SIGNED-001"))
                .thenThrow(new RuntimeException("网络超时"));

        assertThatNoException().isThrownBy(() -> service.refreshAndProgress(sample));

        assertThat(sample.getStatus()).isEqualTo(3);
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("status 为 null 时跳过")
    void refreshAndProgress_nullStatus_skip() {
        SampleRequest sample = shippingSample("TEST-SIGNED-001", "SF");
        sample.setStatus(null);

        service.refreshAndProgress(sample);

        verify(logisticsGateway, never()).queryTrack(any(), any());
    }
}
