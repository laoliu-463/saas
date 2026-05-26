package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeCommand;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeResult;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsSubscriptionServiceTest {

    @Mock private Kuaidi100LogisticsGateway logisticsGateway;
    @Mock private ObjectProvider<Kuaidi100LogisticsGateway> logisticsGatewayProvider;
    @Mock private SampleRequestMapper sampleRequestMapper;

    private LogisticsProperties properties;
    private SampleLogisticsSubscriptionService service;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        properties.setProvider("kuaidi100");
        properties.getKd100().setEnabled(true);
        properties.getKd100().setSubscribeEnabled(true);
        lenient().when(logisticsGatewayProvider.getIfAvailable()).thenReturn(logisticsGateway);
        service = new SampleLogisticsSubscriptionService(properties, logisticsGatewayProvider, sampleRequestMapper);
    }

    @Test
    void subscribeAfterShipment_shouldMarkSubscribedForSuccessOrDuplicateReturnCode() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = sample(id);
        when(logisticsGateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .sampleRequestId(id)
                .companyCode("shunfeng")
                .trackingNo("SF123456789")
                .phone("13800138000")
                .to("深圳市南山区")
                .build())).thenReturn(LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .companyCode("shunfeng")
                .trackingNo("SF123456789")
                .success(true)
                .returnCode("501")
                .message("重复订阅")
                .rawResponse(Map.of("returnCode", "501"))
                .build());
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isTrue();
        assertThat(sample.getLogisticsProvider()).isEqualTo("KUAIDI100");
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("SUBSCRIBED");
        assertThat(sample.getLogisticsSubscribedAt()).isNotNull();
        assertThat(sample.getLogisticsLastSubscribeAt()).isNotNull();
        assertThat(sample.getLogisticsExceptionReason()).isNull();
        verify(sampleRequestMapper).updateById(sample);
    }

    @Test
    void subscribeAfterShipment_shouldRecordFailureWithoutThrowing() {
        SampleRequest sample = sample(UUID.randomUUID());
        when(logisticsGateway.subscribeTrack(any(LogisticsSubscribeCommand.class)))
                .thenReturn(LogisticsSubscribeResult.builder()
                        .provider("KUAIDI100")
                        .success(false)
                        .returnCode("700")
                        .message("参数错误")
                        .rawResponse(Map.of("returnCode", "700"))
                        .build());
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isFalse();
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("FAILED");
        assertThat(sample.getLogisticsExceptionReason()).contains("参数错误");
        verify(sampleRequestMapper).updateById(sample);
    }

    @Test
    void subscribeAfterShipment_shouldFailWhenConcreteKuaidi100GatewayMissing() {
        SampleRequest sample = sample(UUID.randomUUID());
        when(logisticsGatewayProvider.getIfAvailable()).thenReturn(null);
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("快递100网关未配置");
        assertThat(sample.getLogisticsProvider()).isEqualTo("KUAIDI100");
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("FAILED");
        assertThat(sample.getLogisticsExceptionReason()).isEqualTo("快递100网关未配置");
        verify(logisticsGateway, never()).subscribeTrack(any());
        verify(sampleRequestMapper).updateById(sample);
    }

    @Test
    void subscribeAfterShipment_shouldSkipWhenSubscribeDisabledOrTrackingMissing() {
        properties.getKd100().setSubscribeEnabled(false);
        SampleRequest disabled = sample(UUID.randomUUID());

        SampleLogisticsSubscriptionService.SubscribeAttemptResult disabledResult = service.subscribeAfterShipment(disabled);

        assertThat(disabledResult.skipped()).isTrue();
        verify(logisticsGateway, never()).subscribeTrack(any());

        properties.getKd100().setSubscribeEnabled(true);
        SampleRequest noTracking = sample(UUID.randomUUID());
        noTracking.setTrackingNo(" ");

        SampleLogisticsSubscriptionService.SubscribeAttemptResult noTrackingResult = service.subscribeAfterShipment(noTracking);

        assertThat(noTrackingResult.skipped()).isTrue();
        verify(logisticsGateway, never()).subscribeTrack(any());
    }

    @Test
    void subscribeAfterShipment_shouldSkipWhenSampleNullOrProviderDisabled() {
        SampleLogisticsSubscriptionService.SubscribeAttemptResult nullSample = service.subscribeAfterShipment(null);
        assertThat(nullSample.skipped()).isTrue();
        assertThat(nullSample.message()).isEqualTo("缺少物流单号");

        properties.setProvider(" ");
        SampleLogisticsSubscriptionService.SubscribeAttemptResult blankProvider =
                service.subscribeAfterShipment(sample(UUID.randomUUID()));
        assertThat(blankProvider.skipped()).isTrue();
        assertThat(blankProvider.message()).isEqualTo("快递100订阅未启用");

        properties.setProvider("kuaidi100");
        properties.getKd100().setEnabled(false);
        SampleLogisticsSubscriptionService.SubscribeAttemptResult disabledProvider =
                service.subscribeAfterShipment(sample(UUID.randomUUID()));
        assertThat(disabledProvider.skipped()).isTrue();

        verify(logisticsGateway, never()).subscribeTrack(any());
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void subscribeAfterShipment_shouldFailAndPersistWhenCompanyCodeMissing() {
        SampleRequest sample = sample(UUID.randomUUID());
        sample.setShipperCode(" ");
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("快递公司编码不能为空");
        assertThat(sample.getLogisticsProvider()).isEqualTo("KUAIDI100");
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("FAILED");
        assertThat(sample.getLogisticsExceptionReason()).isEqualTo("快递公司编码不能为空");
        assertThat(sample.getLogisticsLastSubscribeAt()).isNotNull();
        verify(logisticsGateway, never()).subscribeTrack(any());
        verify(sampleRequestMapper).updateById(sample);
    }

    @Test
    void subscribeAfterShipment_shouldHandleNullAndBlankFailureMessages() {
        SampleRequest nullResponseSample = sample(UUID.randomUUID());
        when(logisticsGateway.subscribeTrack(any(LogisticsSubscribeCommand.class))).thenReturn(null);
        when(sampleRequestMapper.updateById(nullResponseSample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult nullResponse =
                service.subscribeAfterShipment(nullResponseSample);

        assertThat(nullResponse.success()).isFalse();
        assertThat(nullResponse.message()).isEqualTo("订阅返回空响应");
        assertThat(nullResponseSample.getLogisticsExceptionReason()).isEqualTo("订阅返回空响应");

        SampleRequest blankMessageSample = sample(UUID.randomUUID());
        when(logisticsGateway.subscribeTrack(any(LogisticsSubscribeCommand.class))).thenReturn(LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .success(false)
                .returnCode("600")
                .message(" ")
                .rawResponse(Map.of())
                .build());
        when(sampleRequestMapper.updateById(blankMessageSample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult blankMessage =
                service.subscribeAfterShipment(blankMessageSample);

        assertThat(blankMessage.success()).isFalse();
        assertThat(blankMessage.message()).isBlank();
        assertThat(blankMessageSample.getLogisticsExceptionReason()).isEqualTo("订阅失败");
    }

    @Test
    void subscribeAfterShipment_shouldKeepExistingSubscribedAtOnSuccess() {
        SampleRequest sample = sample(UUID.randomUUID());
        var subscribedAt = java.time.LocalDateTime.of(2026, 5, 25, 8, 0);
        sample.setLogisticsSubscribedAt(subscribedAt);
        when(logisticsGateway.subscribeTrack(any(LogisticsSubscribeCommand.class))).thenReturn(LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .success(true)
                .returnCode("200")
                .message("ok")
                .rawResponse(Map.of("returnCode", "200"))
                .build());
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isTrue();
        assertThat(sample.getLogisticsSubscribedAt()).isEqualTo(subscribedAt);
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("SUBSCRIBED");
    }

    @Test
    void subscribeAfterShipment_shouldReturnFailureEvenWhenGatewayOrPersistenceThrows() {
        SampleRequest sample = sample(UUID.randomUUID());
        sample.setTrackingNo("123456");
        when(logisticsGateway.subscribeTrack(any(LogisticsSubscribeCommand.class)))
                .thenThrow(new IllegalStateException("remote down"));
        when(sampleRequestMapper.updateById(sample)).thenThrow(new RuntimeException("db down"));

        SampleLogisticsSubscriptionService.SubscribeAttemptResult result = service.subscribeAfterShipment(sample);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("remote down");
        assertThat(sample.getLogisticsSubscribeStatus()).isEqualTo("FAILED");
        assertThat(sample.getLogisticsExceptionReason()).isEqualTo("remote down");
        verify(sampleRequestMapper).updateById(sample);
    }

    private SampleRequest sample(UUID id) {
        SampleRequest sample = new SampleRequest();
        sample.setId(id);
        sample.setRequestNo("SM-SUB");
        sample.setShipperCode("shunfeng");
        sample.setTrackingNo("SF123456789");
        sample.setRecipientPhone("13800138000");
        sample.setRecipientAddress("深圳市南山区");
        sample.setVersion(0);
        return sample;
    }
}
