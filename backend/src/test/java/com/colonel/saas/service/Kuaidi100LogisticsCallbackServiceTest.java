package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Kuaidi100LogisticsCallbackServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private SampleDomainEventPublisher sampleDomainEventPublisher;

    private LogisticsProperties properties;
    private Kuaidi100LogisticsCallbackService service;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        properties.getKd100().setCallbackSalt("callback-salt");
        service = new Kuaidi100LogisticsCallbackService(
                properties,
                sampleRequestMapper,
                sampleLogisticsTraceMapper,
                sampleStatusLogService,
                sampleDomainEventPublisher,
                new ObjectMapper());
    }

    @Test
    void handleCallback_shouldInsertUniqueTraceAndProgressSignedSample() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, 3, "shunfeng", "SF123456789");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String param = signedParam("3", "派件已签收");
        Kuaidi100LogisticsCallbackService.CallbackAck ack = service.handleCallback(param, sign(param));

        assertThat(ack.result()).isTrue();
        assertThat(ack.returnCode()).isEqualTo("200");

        ArgumentCaptor<SampleLogisticsTrace> traceCaptor = ArgumentCaptor.forClass(SampleLogisticsTrace.class);
        verify(sampleLogisticsTraceMapper).insert(traceCaptor.capture());
        SampleLogisticsTrace trace = traceCaptor.getValue();
        assertThat(trace.getSampleRequestId()).isEqualTo(sampleId);
        assertThat(trace.getNodeHash()).isNotBlank();
        assertThat(trace.getLocation()).isEqualTo("深圳");
        assertThat(trace.getRawPayload()).containsEntry("context", "派件已签收");

        ArgumentCaptor<SampleRequest> sampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(sampleCaptor.capture());
        SampleRequest updated = sampleCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(5);
        assertThat(updated.getSignedAt()).isEqualTo(LocalDateTime.of(2026, 5, 25, 10, 30));
        assertThat(updated.getDeliverTime()).isEqualTo(LocalDateTime.of(2026, 5, 25, 10, 30));
        assertThat(updated.getLogisticsProvider()).isEqualTo("KUAIDI100");
        assertThat(updated.getLogisticsCallbackStatus()).isEqualTo("polling");
        assertThat(updated.getLogisticsLastCallbackAt()).isNotNull();
        assertThat(updated.getLogisticsRawPayload()).containsKey("lastResult");
        verify(sampleStatusLogService).log(sampleId, 3, 5, sample.getUserId(), "快递100签收回调自动推进");
        verify(sampleDomainEventPublisher).publishSampleSigned(sample, LocalDateTime.of(2026, 5, 25, 10, 30));
    }

    @Test
    void handleCallback_shouldDeduplicateTraceAndKeepInTransitStatus() {
        SampleRequest sample = sample(UUID.randomUUID(), 3, "shunfeng", "SF123456789");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(1L);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String param = signedParam("0", "快件已揽收");
        Kuaidi100LogisticsCallbackService.CallbackAck ack = service.handleCallback(param, sign(param));

        assertThat(ack.result()).isTrue();
        verify(sampleLogisticsTraceMapper, never()).insert(any());
        ArgumentCaptor<SampleRequest> sampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(sampleCaptor.capture());
        assertThat(sampleCaptor.getValue().getStatus()).isEqualTo(3);
        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());
    }

    @Test
    void handleCallback_shouldFailWhenSampleUpdateLosesOptimisticLock() {
        SampleRequest sample = sample(UUID.randomUUID(), 3, "shunfeng", "SF123456789");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleRequestMapper.updateById(any())).thenReturn(0);

        String param = callbackParam("polling", "ok", "0", "SF", "SF123456789", "[]");

        assertThatThrownBy(() -> service.handleCallback(param, sign(param)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已被他人修改");

        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());
    }

    @Test
    void handleCallback_shouldRejectInvalidSignAndMismatchedWaybillWithoutUpdating() {
        String param = signedParam("3", "派件已签收");

        Kuaidi100LogisticsCallbackService.CallbackAck invalidSign = service.handleCallback(param, "bad-sign");

        assertThat(invalidSign.result()).isFalse();
        assertThat(invalidSign.returnCode()).isEqualTo("500");
        verify(sampleRequestMapper, never()).selectList(any());
        verify(sampleRequestMapper, never()).updateById(any());

        SampleRequest mismatch = sample(UUID.randomUUID(), 3, "yuantong", "SF123456789");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(mismatch));

        Kuaidi100LogisticsCallbackService.CallbackAck mismatchAck = service.handleCallback(param, sign(param));

        assertThat(mismatchAck.result()).isFalse();
        assertThat(mismatchAck.message()).contains("不一致");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void handleCallback_shouldRejectMissingOrMalformedPayloadsWithoutUpdating() {
        Kuaidi100LogisticsCallbackService.CallbackAck missingParam = service.handleCallback(" ", "SIGN");
        assertThat(missingParam.result()).isFalse();
        assertThat(missingParam.message()).isEqualTo("缺少参数");

        String malformed = "{not-json";
        Kuaidi100LogisticsCallbackService.CallbackAck malformedAck = service.handleCallback(malformed, sign(malformed));
        assertThat(malformedAck.result()).isFalse();
        assertThat(malformedAck.message()).isEqualTo("参数解析失败");

        String missingLastResult = "{}";
        Kuaidi100LogisticsCallbackService.CallbackAck missingLastResultAck =
                service.handleCallback(missingLastResult, sign(missingLastResult));
        assertThat(missingLastResultAck.result()).isFalse();
        assertThat(missingLastResultAck.message()).isEqualTo("缺少 lastResult");

        String nonObjectLastResult = """
                {"lastResult":"bad"}
                """;
        Kuaidi100LogisticsCallbackService.CallbackAck nonObjectAck =
                service.handleCallback(nonObjectLastResult, sign(nonObjectLastResult));
        assertThat(nonObjectAck.result()).isFalse();
        assertThat(nonObjectAck.message()).isEqualTo("缺少 lastResult");

        String missingCompany = """
                {"lastResult":{"nu":"SF123456789"}}
                """;
        Kuaidi100LogisticsCallbackService.CallbackAck missingCompanyAck =
                service.handleCallback(missingCompany, sign(missingCompany));
        assertThat(missingCompanyAck.result()).isFalse();
        assertThat(missingCompanyAck.message()).isEqualTo("缺少快递公司或物流单号");

        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void handleCallback_shouldRejectWhenCallbackSaltMissing() {
        properties.getKd100().setCallbackSalt(" ");
        String param = signedParam("3", "派件已签收");

        Kuaidi100LogisticsCallbackService.CallbackAck ack = service.handleCallback(param, sign(param));

        assertThat(ack.result()).isFalse();
        assertThat(ack.message()).isEqualTo("签名错误");
        verify(sampleRequestMapper, never()).selectList(any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void handleCallback_shouldRecordExceptionReasonFromLatestNodeOrCallbackMessage() {
        SampleRequest withNodeContext = sample(UUID.randomUUID(), 3, "ZTO", "ZTO001");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(withNodeContext));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(null);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String withNodeParam = callbackParam("shutdown", "上游关闭", "4", "ZTO", "ZTO001", """
                [
                  {
                    "time": "bad-time",
                    "context": "包裹滞留",
                    "location": "杭州",
                    "statusCode": "4"
                  }
                ]
                """);
        Kuaidi100LogisticsCallbackService.CallbackAck withNodeAck =
                service.handleCallback(withNodeParam, sign(withNodeParam));

        assertThat(withNodeAck.result()).isTrue();
        ArgumentCaptor<SampleRequest> firstSampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(firstSampleCaptor.capture());
        SampleRequest firstUpdated = firstSampleCaptor.getValue();
        assertThat(firstUpdated.getLogisticsStatusName()).isEqualTo("EXCEPTION");
        assertThat(firstUpdated.getLogisticsExceptionReason()).isEqualTo("包裹滞留");
        assertThat(firstUpdated.getSignedAt()).isNull();
        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());

        SampleRequest withoutNodeContext = sample(UUID.randomUUID(), 3, "ZTO", "ZTO002");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(withoutNodeContext));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String noDataParam = callbackParam("abort", "运单关闭", "14", "ZTO", "ZTO002", "\"bad-data\"");
        Kuaidi100LogisticsCallbackService.CallbackAck noDataAck =
                service.handleCallback(noDataParam, sign(noDataParam));

        assertThat(noDataAck.result()).isTrue();
        ArgumentCaptor<SampleRequest> secondSampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper, org.mockito.Mockito.times(2)).updateById(secondSampleCaptor.capture());
        SampleRequest secondUpdated = secondSampleCaptor.getAllValues().get(1);
        assertThat(secondUpdated.getLogisticsCallbackStatus()).isEqualTo("abort");
        assertThat(secondUpdated.getLogisticsExceptionReason()).isEqualTo("运单关闭");
    }

    @Test
    void handleCallback_shouldProgressPendingShipWithFallbackSignedAtAndOperatorId() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, 2, "JD", "JD001");
        sample.setUserId(null);
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String param = callbackParam("polling", "ok", "3", "JINGDONG", "JD001", """
                [
                  {
                    "time": "bad-time",
                    "context": "投递完成",
                    "location": "北京"
                  }
                ]
                """);
        Kuaidi100LogisticsCallbackService.CallbackAck ack = service.handleCallback(param, sign(param));

        assertThat(ack.result()).isTrue();
        ArgumentCaptor<SampleRequest> sampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(sampleCaptor.capture());
        SampleRequest updated = sampleCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(5);
        assertThat(updated.getSignedAt()).isNotNull();
        assertThat(updated.getDeliverTime()).isEqualTo(updated.getSignedAt());
        verify(sampleStatusLogService).log(sampleId, 2, 5, sampleId, "快递100签收回调自动推进");
        verify(sampleDomainEventPublisher).publishSampleSigned(sample, updated.getSignedAt());
    }

    @Test
    void handleCallback_shouldNotProgressWhenSignedSampleAlreadyTerminal() {
        SampleRequest sample = sample(UUID.randomUUID(), 8, "EMS", "EMS001");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String param = callbackParam("polling", "ok", "3", "EMS", "EMS001", """
                [
                  {
                    "time": "2026-05-25 11:30:00",
                    "context": "派件已签收",
                    "location": "广州"
                  }
                ]
                """);
        Kuaidi100LogisticsCallbackService.CallbackAck ack = service.handleCallback(param, sign(param));

        assertThat(ack.result()).isTrue();
        ArgumentCaptor<SampleRequest> sampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(sampleCaptor.capture());
        SampleRequest updated = sampleCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(8);
        assertThat(updated.getSignedAt()).isNull();
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
        verify(sampleDomainEventPublisher, never()).publishSampleSigned(any(), any());
    }

    @Test
    void handleCallback_shouldNormalizeAliasesAndMapStatusNames() {
        SampleRequest deliveringSample = sample(UUID.randomUUID(), 3, "YD", "YD001");
        when(sampleRequestMapper.selectList(any())).thenReturn(Arrays.asList(null, deliveringSample));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String deliveringParam = callbackParam("polling", "ok", "5", "YUNDA", "YD001", "[]");
        Kuaidi100LogisticsCallbackService.CallbackAck deliveringAck =
                service.handleCallback(deliveringParam, sign(deliveringParam));

        assertThat(deliveringAck.result()).isTrue();
        ArgumentCaptor<SampleRequest> sampleCaptor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(sampleCaptor.capture());
        assertThat(sampleCaptor.getValue().getLogisticsStatusName()).isEqualTo("DELIVERING");
        verify(sampleLogisticsTraceMapper, never()).insert(any());

        SampleRequest unknownSample = sample(UUID.randomUUID(), 3, "unknown-express", "UNK001");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(unknownSample));
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        String unknownParam = callbackParam("polling", "ok", "99", "unknown-express", "UNK001", "[]");
        Kuaidi100LogisticsCallbackService.CallbackAck unknownAck =
                service.handleCallback(unknownParam, sign(unknownParam));

        assertThat(unknownAck.result()).isTrue();
        verify(sampleRequestMapper, org.mockito.Mockito.times(2)).updateById(sampleCaptor.capture());
        List<SampleRequest> capturedSamples = sampleCaptor.getAllValues();
        assertThat(capturedSamples.get(capturedSamples.size() - 1).getLogisticsStatusName()).isEqualTo("UNKNOWN");
    }

    private SampleRequest sample(UUID id, int status, String company, String trackingNo) {
        SampleRequest sample = new SampleRequest();
        sample.setId(id);
        sample.setRequestNo("SM-CALLBACK");
        sample.setStatus(status);
        sample.setShipperCode(company);
        sample.setTrackingNo(trackingNo);
        sample.setUserId(UUID.randomUUID());
        sample.setVersion(0);
        return sample;
    }

    private String signedParam(String state, String context) {
        return """
                {
                  "status": "polling",
                  "message": "监控中",
                  "lastResult": {
                    "status": "200",
                    "message": "ok",
                    "state": "%s",
                    "com": "shunfeng",
                    "nu": "SF123456789",
                    "data": [
                      {
                        "time": "2026-05-25 10:30:00",
                        "ftime": "2026-05-25 10:30:00",
                        "context": "%s",
                        "location": "深圳",
                        "status": "签收",
                        "statusCode": "3"
                      }
                    ]
                  }
                }
                """.formatted(state, context);
    }

    private String callbackParam(String status, String message, String state, String company, String trackingNo, String dataJson) {
        return """
                {
                  "status": "%s",
                  "message": "%s",
                  "lastResult": {
                    "status": "200",
                    "message": "ok",
                    "state": "%s",
                    "com": "%s",
                    "nu": "%s",
                    "data": %s
                  }
                }
                """.formatted(status, message, state, company, trackingNo, dataJson);
    }

    private String sign(String param) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((param + "callback-salt").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
