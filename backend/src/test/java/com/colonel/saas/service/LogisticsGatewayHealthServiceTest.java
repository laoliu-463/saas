package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.query.Kuaidi100LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.KuaidiNiaoLogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsGatewayHealthServiceTest {

    private LogisticsProperties properties;
    private LogisticsGatewayHealthService service;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        service = new LogisticsGatewayHealthService(
                properties,
                new MockLogisticsQueryGateway(),
                new KuaidiNiaoLogisticsQueryGateway(properties, null),
                new Kuaidi100LogisticsQueryGateway(properties, null),
                true);
    }

    @Test
    void diagnoseCurrentProvider_mock_shouldReturnMockOnly() {
        properties.setProvider("mock");
        var response = service.diagnoseCurrentProvider();
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.MOCK_ONLY);
    }

    @Test
    void diagnoseProvider_kuaidiniaoMissingCredentials_shouldReturnNotConfigured() {
        properties.setProvider("kuaidiniao");
        properties.getKdn().setEnabled(true);
        var response = service.diagnoseProvider("kuaidiniao");
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
        assertThat(response.getMessage()).contains("凭证未配置");
    }

    @Test
    void diagnoseProvider_shouldNormalizeUnknownAndDisabledProviders() {
        properties.setProvider(" kuaidi100 ");
        var disabled = service.diagnoseCurrentProvider();
        var unknown = service.diagnoseProvider(" unknown ");

        assertThat(disabled.getProvider()).isEqualTo("kuaidi100");
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
        assertThat(disabled.getMessage()).contains("未启用");
        assertThat(unknown.getProvider()).isEqualTo("unknown");
        assertThat(unknown.getMessage()).contains("未知 provider");
    }

    @Test
    void testQuery_shouldBlockRealApiInTestEnv() {
        properties.setProvider("kuaidiniao");
        properties.getKdn().setEnabled(true);
        properties.getKdn().setEbusinessId("id");
        properties.getKdn().setApiKey("key");
        LogisticsGatewayTestRequest request = new LogisticsGatewayTestRequest();
        request.setProvider("kuaidiniao");
        request.setLogisticsCompany("YTO");
        request.setTrackingNo("YT1234567890");
        var response = service.testQuery(request);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("test 环境禁止");
    }

    @Test
    void testQuery_mockProvider_shouldReturnMockOnlyWithoutRealCall() {
        LogisticsGatewayTestRequest request = request(" mock ", "SF", "SF123456");

        var response = service.testQuery(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("mock");
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.MOCK_ONLY);
        assertThat(response.isRawPayloadStored()).isFalse();
    }

    @Test
    void testQuery_notConfiguredRealProvider_shouldReturnNotConfigured() {
        LogisticsGatewayTestRequest request = request("kuaidi100", "SF", "SF123456");

        var response = service.testQuery(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
        assertThat(response.getMessage()).contains("未启用");
    }

    @Test
    void testQuery_success_shouldPersistLastHealthStatusForSandboxAndReal() {
        properties.getKdn().setEnabled(true);
        properties.getKdn().setEbusinessId("id");
        properties.getKdn().setApiKey("key");
        properties.getKdn().setSandboxEnabled(true);
        KuaidiNiaoLogisticsQueryGateway kdn = mock(KuaidiNiaoLogisticsQueryGateway.class);
        when(kdn.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123456")
                .build())).thenReturn(LogisticsQueryResult.builder()
                .success(true)
                .provider("KUAIDINIAO")
                .trackingNo("SF123456")
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.IN_TRANSIT)
                .rawPayload(Map.of("ok", true))
                .build());
        LogisticsGatewayHealthService sandboxService = newService(false, kdn, mock(Kuaidi100LogisticsQueryGateway.class));

        var response = sandboxService.testQuery(request("kuaidiniao", "SF", "SF123456"));
        var health = sandboxService.diagnoseProvider("kuaidiniao");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.SANDBOX_PASSED);
        assertThat(response.isRawPayloadStored()).isTrue();
        assertThat(health.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.SANDBOX_PASSED);

        properties.getKdn().setSandboxEnabled(false);
        var realResponse = sandboxService.testQuery(request("kuaidiniao", "SF", "SF123456"));
        assertThat(realResponse.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.REAL_CONNECTED);
        assertThat(sandboxService.diagnoseProvider("kuaidiniao").getStatus())
                .isEqualTo(LogisticsGatewayHealthStatus.REAL_CONNECTED);
    }

    @Test
    void testQuery_failedOrNotConfiguredQueryResult_shouldMapStatus() {
        properties.getKd100().setEnabled(true);
        properties.getKd100().setCustomer("customer");
        properties.getKd100().setKey("key");
        Kuaidi100LogisticsQueryGateway kd100 = mock(Kuaidi100LogisticsQueryGateway.class);
        when(kd100.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("FAIL")
                .build())).thenReturn(LogisticsQueryResult.queryFailed(
                "KUAIDI100", "SF", "FAIL", "REMOTE", "远端失败"));
        when(kd100.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("NC")
                .build())).thenReturn(LogisticsQueryResult.notConfigured("KUAIDI100", "SF", "NC"));
        LogisticsGatewayHealthService realService = newService(false, mock(KuaidiNiaoLogisticsQueryGateway.class), kd100);

        var failed = realService.testQuery(request("kuaidi100", "SF", "FAIL"));
        var notConfigured = realService.testQuery(request("kuaidi100", "SF", "NC"));

        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.FAILED);
        assertThat(realService.diagnoseProvider("kuaidi100").getStatus())
                .isEqualTo(LogisticsGatewayHealthStatus.FAILED);
        assertThat(notConfigured.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
    }

    @Test
    void testQuery_shouldPassOptionalPhoneAndAddressToProviderCommand() {
        properties.getKd100().setEnabled(true);
        properties.getKd100().setCustomer("customer");
        properties.getKd100().setKey("key");
        Kuaidi100LogisticsQueryGateway kd100 = mock(Kuaidi100LogisticsQueryGateway.class);
        when(kd100.query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123456")
                .phone("13800138000")
                .from("广东省广州市")
                .to("广东省深圳市")
                .build())).thenReturn(LogisticsQueryResult.builder()
                .success(true)
                .provider("KUAIDI100")
                .trackingNo("SF123456")
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.IN_TRANSIT)
                .rawPayload(Map.of("ok", true))
                .build());
        LogisticsGatewayHealthService realService = newService(false, mock(KuaidiNiaoLogisticsQueryGateway.class), kd100);

        LogisticsGatewayTestRequest request = request("kuaidi100", "SF", "SF123456");
        request.setPhone("13800138000");
        request.setFrom("广东省广州市");
        request.setTo("广东省深圳市");
        var response = realService.testQuery(request);

        assertThat(response.isSuccess()).isTrue();
        verify(kd100).query(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123456")
                .phone("13800138000")
                .from("广东省广州市")
                .to("广东省深圳市")
                .build());
    }

    @Test
    void maskTrackingNo_shouldMaskMiddlePart() {
        assertThat(LogisticsGatewayHealthService.maskTrackingNo("SF1234567890")).isEqualTo("SF1***90");
        assertThat(LogisticsGatewayHealthService.maskTrackingNo(" 12345 ")).isEqualTo("***");
        assertThat(LogisticsGatewayHealthService.maskTrackingNo(" ")).isEqualTo("***");
    }

    private LogisticsGatewayHealthService newService(
            boolean testEnabled,
            KuaidiNiaoLogisticsQueryGateway kdn,
            Kuaidi100LogisticsQueryGateway kd100) {
        return new LogisticsGatewayHealthService(
                properties,
                new MockLogisticsQueryGateway(),
                kdn,
                kd100,
                testEnabled);
    }

    private static LogisticsGatewayTestRequest request(String provider, String company, String trackingNo) {
        LogisticsGatewayTestRequest request = new LogisticsGatewayTestRequest();
        request.setProvider(provider);
        request.setLogisticsCompany(company);
        request.setTrackingNo(trackingNo);
        return request;
    }
}
