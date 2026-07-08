package com.colonel.saas.domain.logistics.application;

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
import static org.mockito.Mockito.when;

/**
 * LogisticsGatewayHealthApplicationService 单元测试（DDD-LOGISTICS-001 Slice 1）。
 *
 * <p>原 LogisticsGatewayHealthServiceTest 中针对 diagnose* / testQuery / maskTrackingNo
 * 的业务断言已迁移到 Application；Service 委派壳为 1-line delegate，单独测试由集成覆盖。</p>
 */
class LogisticsGatewayHealthApplicationServiceTest {

    private LogisticsProperties properties;
    private LogisticsGatewayHealthApplicationService applicationService;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        applicationService = newApplicationService(false, mock(KuaidiNiaoLogisticsQueryGateway.class), mock(Kuaidi100LogisticsQueryGateway.class));
    }

    @Test
    void diagnoseCurrentProvider_mock_shouldReturnMockOnly() {
        properties.setProvider("mock");
        var response = applicationService.diagnoseCurrentProvider();
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.MOCK_ONLY);
    }

    @Test
    void diagnoseProvider_kuaidiniaoMissingCredentials_shouldReturnNotConfigured() {
        properties.setProvider("kuaidiniao");
        properties.getKdn().setEnabled(true);
        var response = applicationService.diagnoseProvider("kuaidiniao");
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
        assertThat(response.getMessage()).contains("凭证未配置");
    }

    @Test
    void diagnoseProvider_shouldNormalizeUnknownAndDisabledProviders() {
        properties.setProvider(" kuaidi100 ");
        var disabled = applicationService.diagnoseCurrentProvider();
        var unknown = applicationService.diagnoseProvider(" unknown ");

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
        LogisticsGatewayHealthApplicationService testModeApp = newApplicationService(true,
                mock(KuaidiNiaoLogisticsQueryGateway.class), mock(Kuaidi100LogisticsQueryGateway.class));
        LogisticsGatewayTestRequest request = new LogisticsGatewayTestRequest();
        request.setProvider("kuaidiniao");
        request.setLogisticsCompany("YTO");
        request.setTrackingNo("YT1234567890");

        var response = testModeApp.testQuery(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("test 环境禁止");
    }

    @Test
    void testQuery_mockProvider_shouldReturnMockOnlyWithoutRealCall() {
        LogisticsGatewayTestRequest request = request(" mock ", "SF", "SF123456");

        var response = applicationService.testQuery(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("mock");
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.MOCK_ONLY);
        assertThat(response.isRawPayloadStored()).isFalse();
    }

    @Test
    void testQuery_notConfiguredRealProvider_shouldReturnNotConfigured() {
        LogisticsGatewayTestRequest request = request("kuaidi100", "SF", "SF123456");

        var response = applicationService.testQuery(request);

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
        LogisticsGatewayHealthApplicationService sandboxApp = newApplicationService(false, kdn, mock(Kuaidi100LogisticsQueryGateway.class));

        var response = sandboxApp.testQuery(request("kuaidiniao", "SF", "SF123456"));
        var health = sandboxApp.diagnoseProvider("kuaidiniao");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.SANDBOX_PASSED);
        assertThat(response.isRawPayloadStored()).isTrue();
        assertThat(health.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.SANDBOX_PASSED);

        properties.getKdn().setSandboxEnabled(false);
        var realResponse = sandboxApp.testQuery(request("kuaidiniao", "SF", "SF123456"));
        assertThat(realResponse.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.REAL_CONNECTED);
        assertThat(sandboxApp.diagnoseProvider("kuaidiniao").getStatus())
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
        LogisticsGatewayHealthApplicationService realApp = newApplicationService(false,
                mock(KuaidiNiaoLogisticsQueryGateway.class), kd100);

        var failed = realApp.testQuery(request("kuaidi100", "SF", "FAIL"));
        var notConfigured = realApp.testQuery(request("kuaidi100", "SF", "NC"));

        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.FAILED);
        assertThat(realApp.diagnoseProvider("kuaidi100").getStatus())
                .isEqualTo(LogisticsGatewayHealthStatus.FAILED);
        assertThat(notConfigured.getStatus()).isEqualTo(LogisticsGatewayHealthStatus.NOT_CONFIGURED);
    }

    @Test
    void maskTrackingNo_shouldMaskMiddlePart() {
        assertThat(LogisticsGatewayHealthApplicationService.maskTrackingNo("SF1234567890")).isEqualTo("SF1***90");
        assertThat(LogisticsGatewayHealthApplicationService.maskTrackingNo(" 12345 ")).isEqualTo("***");
        assertThat(LogisticsGatewayHealthApplicationService.maskTrackingNo("")).isEqualTo("***");
    }

    private LogisticsGatewayHealthApplicationService newApplicationService(
            boolean testEnabled,
            KuaidiNiaoLogisticsQueryGateway kdn,
            Kuaidi100LogisticsQueryGateway kd100) {
        return new LogisticsGatewayHealthApplicationService(
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
