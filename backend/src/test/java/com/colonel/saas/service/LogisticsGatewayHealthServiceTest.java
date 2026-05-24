package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.gateway.logistics.query.Kuaidi100LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.KuaidiNiaoLogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void maskTrackingNo_shouldMaskMiddlePart() {
        assertThat(LogisticsGatewayHealthService.maskTrackingNo("SF1234567890")).isEqualTo("SF1***90");
    }
}
