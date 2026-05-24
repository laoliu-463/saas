package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.fallback.ManualFallbackLogisticsGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsGatewayRouterTest {

    @Test
    void kuaidiniaoWithoutCredentials_shouldReturnNotConfigured() {
        LogisticsProperties properties = new LogisticsProperties();
        properties.setProvider("kuaidiniao");
        LogisticsGateway fallback = new ManualFallbackLogisticsGateway();
        MockLogisticsQueryGateway mock = new MockLogisticsQueryGateway();
        KuaidiNiaoLogisticsQueryGateway kdn = new KuaidiNiaoLogisticsQueryGateway(properties, fallback);
        Kuaidi100LogisticsQueryGateway kd100 = new Kuaidi100LogisticsQueryGateway(properties, fallback);
        LogisticsGatewayRouter router = new LogisticsGatewayRouter(properties, mock, kdn, kd100, false);

        LogisticsQueryResult result = router.query("SF", "SF1234567890");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.NOT_CONFIGURED);
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
    }
}
