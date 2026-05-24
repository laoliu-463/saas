package com.colonel.saas.service;

import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsSyncServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private LogisticsQueryGateway logisticsQueryGateway;
    @Mock private SampleDomainEventPublisher sampleDomainEventPublisher;

    private SampleLogisticsSyncService service;

    @BeforeEach
    void setUp() {
        service = new SampleLogisticsSyncService(
                logisticsQueryGateway,
                sampleRequestMapper,
                sampleLogisticsTraceMapper,
                sampleStatusLogService,
                sampleDomainEventPublisher);
    }

    @Test
    void signedResult_shouldProgressToPendingHomework() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = sample(3, "MOCK-SIGNED");
        sample.setId(id);

        when(sampleRequestMapper.selectById(id)).thenReturn(sample);
        when(logisticsQueryGateway.query("SF", "MOCK-SIGNED")).thenReturn(signedResult("MOCK-SIGNED"));
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSigned()).isTrue();
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(5);
        verify(sampleStatusLogService).log(any(), any(), any(), any(), any());
        verify(sampleDomainEventPublisher).publishSampleSigned(any(), any());
    }

    @Test
    void queryFailure_shouldNotChangeMainStatus() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = sample(3, "FAIL-001");
        sample.setId(id);

        when(sampleRequestMapper.selectById(id)).thenReturn(sample);
        when(logisticsQueryGateway.query("SF", "FAIL-001"))
                .thenReturn(LogisticsQueryResult.queryFailed("MOCK", "SF", "FAIL-001", "REMOTE_ERROR", "查询失败"));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isFalse();
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(3);
        assertThat(captor.getValue().getLogisticsLastError()).isNotBlank();
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());
    }

    @Test
    void syncOne_shouldReturnNotFoundWhenSampleMissing() {
        UUID id = UUID.randomUUID();
        when(sampleRequestMapper.selectById(id)).thenReturn(null);
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NOT_FOUND");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void syncOne_shouldReturnNoTrackingWhenSampleHasBlankTrackingNo() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = sample(3, " ");
        sample.setId(id);

        when(sampleRequestMapper.selectById(id)).thenReturn(sample);
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NO_TRACKING");
        verify(logisticsQueryGateway, never()).query(any(), any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void syncByTrackingNo_shouldRejectBlankTrackingNo() {
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");

        LogisticsQueryResult result = service.syncByTrackingNo(" ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAM");
        verify(sampleRequestMapper, never()).selectOne(any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void syncByTrackingNo_shouldReturnNotFoundWhenNoSampleMatched() {
        when(sampleRequestMapper.selectOne(any())).thenReturn(null);
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");

        LogisticsQueryResult result = service.syncByTrackingNo(" SF123 ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NOT_FOUND");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void handleLogisticsResult_signed_shouldUpdateSignedAt() {
        SampleRequest sample = sample(3, "MOCK");
        when(sampleRequestMapper.updateById(any())).thenReturn(1);
        when(logisticsQueryGateway.providerName()).thenReturn("MOCK");

        LocalDateTime signedAt = LocalDateTime.now().minusHours(1);
        LogisticsQueryResult result = LogisticsQueryResult.builder()
                .success(true)
                .provider("MOCK")
                .trackingNo("MOCK")
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.SIGNED)
                .statusName("已签收")
                .signed(true)
                .signedAt(signedAt)
                .traces(List.of())
                .queriedAt(LocalDateTime.now())
                .build();

        service.handleLogisticsResult(sample, result);

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSignedAt()).isEqualTo(signedAt);
        verify(sampleDomainEventPublisher).publishSampleSigned(any(), any());
    }

    @Test
    void handleLogisticsResult_shouldReturnInputWhenSampleOrResultMissing() {
        LogisticsQueryResult result = LogisticsQueryResult.queryFailed("MOCK", "SF", "MOCK", "ERR", "失败");

        assertThat(service.handleLogisticsResult(null, result)).isSameAs(result);
        assertThat(service.handleLogisticsResult(sample(3, "MOCK"), null)).isNull();
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void handleLogisticsResult_shouldReplaceAndInsertTraces() {
        SampleRequest sample = sample(3, "MOCK");
        when(sampleRequestMapper.updateById(any())).thenReturn(1);
        LocalDateTime first = LocalDateTime.now().minusHours(2);
        LocalDateTime second = LocalDateTime.now().minusHours(1);
        LogisticsQueryResult result = LogisticsQueryResult.builder()
                .success(true)
                .provider("MOCK")
                .trackingNo("MOCK")
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.IN_TRANSIT)
                .statusName("运输中")
                .traces(List.of(
                        LogisticsQueryResult.LogisticsTraceItem.builder()
                                .traceTime(first)
                                .traceContent("已揽收")
                                .location("杭州")
                                .build(),
                        LogisticsQueryResult.LogisticsTraceItem.builder()
                                .traceTime(second)
                                .traceContent("运输中")
                                .location("上海")
                                .build()))
                .rawPayload(Map.of("state", "moving"))
                .queriedAt(LocalDateTime.now())
                .build();

        service.handleLogisticsResult(sample, result);

        verify(sampleLogisticsTraceMapper).delete(any());
        ArgumentCaptor<SampleLogisticsTrace> traceCaptor = ArgumentCaptor.forClass(SampleLogisticsTrace.class);
        verify(sampleLogisticsTraceMapper, times(2)).insert(traceCaptor.capture());
        assertThat(traceCaptor.getAllValues())
                .extracting(SampleLogisticsTrace::getTraceContent)
                .containsExactly("已揽收", "运输中");
        assertThat(sample.getLogisticsRawPayload()).containsEntry("state", "moving");
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void handleLogisticsResult_signed_shouldNotProgressWhenStatusIsClosed() {
        SampleRequest sample = sample(9, "MOCK");
        when(sampleRequestMapper.updateById(any())).thenReturn(1);
        LogisticsQueryResult result = signedResult("MOCK");

        service.handleLogisticsResult(sample, result);

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(9);
        assertThat(captor.getValue().getSignedAt()).isNull();
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());
    }

    @Test
    void syncPendingInTransit_shouldCountSuccessFailedSkippedAndExceptions() {
        SampleRequest success = sample(3, "OK");
        SampleRequest skipped = sample(3, "SKIP");
        SampleRequest failed = sample(3, "FAIL");
        SampleRequest exception = sample(3, "EX");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(success, skipped, failed, exception));
        when(logisticsQueryGateway.query("SF", "OK")).thenReturn(successResult("OK"));
        when(logisticsQueryGateway.query("SF", "SKIP"))
                .thenReturn(LogisticsQueryResult.notConfigured("MOCK", "SF", "SKIP"));
        when(logisticsQueryGateway.query("SF", "FAIL"))
                .thenReturn(LogisticsQueryResult.queryFailed("MOCK", "SF", "FAIL", "REMOTE_ERROR", "查询失败"));
        when(logisticsQueryGateway.query("SF", "EX")).thenThrow(new IllegalStateException("boom"));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        SampleLogisticsSyncService.SyncBatchSummary summary = service.syncPendingInTransit(0);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
        verify(sampleRequestMapper, times(3)).updateById(any());
    }

    @Test
    void listTraces_shouldDelegateToMapper() {
        UUID sampleRequestId = UUID.randomUUID();
        SampleLogisticsTrace trace = new SampleLogisticsTrace();
        trace.setSampleRequestId(sampleRequestId);
        when(sampleLogisticsTraceMapper.selectList(any())).thenReturn(List.of(trace));

        List<SampleLogisticsTrace> traces = service.listTraces(sampleRequestId);

        assertThat(traces).containsExactly(trace);
        verify(sampleLogisticsTraceMapper).selectList(any());
    }

    private SampleRequest sample(int status, String trackingNo) {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SM-" + trackingNo);
        sample.setStatus(status);
        sample.setTrackingNo(trackingNo);
        sample.setShipperCode("SF");
        sample.setUserId(UUID.randomUUID());
        sample.setVersion(0);
        return sample;
    }

    private LogisticsQueryResult successResult(String trackingNo) {
        return LogisticsQueryResult.builder()
                .success(true)
                .provider("MOCK")
                .trackingNo(trackingNo)
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.IN_TRANSIT)
                .statusName("运输中")
                .signed(false)
                .traces(List.of())
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }

    private LogisticsQueryResult signedResult(String trackingNo) {
        return LogisticsQueryResult.builder()
                .success(true)
                .provider("MOCK")
                .trackingNo(trackingNo)
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.SIGNED)
                .statusName("已签收")
                .signed(true)
                .signedAt(LocalDateTime.now().minusHours(1))
                .traces(List.of())
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }
}
