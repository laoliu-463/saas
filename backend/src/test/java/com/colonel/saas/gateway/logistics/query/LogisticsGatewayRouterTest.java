package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.fallback.ManualFallbackLogisticsGateway;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogisticsGatewayRouterTest {

    @Test
    void kuaidiniaoWithoutCredentials_shouldReturnNotConfigured() {
        LogisticsProperties properties = new LogisticsProperties();
        properties.setProvider("kuaidiniao");
        LogisticsGateway fallback = new ManualFallbackLogisticsGateway();
        MockLogisticsQueryGateway mock = new MockLogisticsQueryGateway();
        KuaidiNiaoLogisticsQueryGateway kdn = new KuaidiNiaoLogisticsQueryGateway(properties, fallback);
        ObjectProvider<Kuaidi100LogisticsGateway> kd100Provider = mock(ObjectProvider.class);
        when(kd100Provider.getIfAvailable()).thenReturn(null);
        Kuaidi100LogisticsQueryGateway kd100 = new Kuaidi100LogisticsQueryGateway(properties, kd100Provider);
        LogisticsGatewayRouter router = new LogisticsGatewayRouter(properties, mock, kdn, kd100, false);

        LogisticsQueryResult result = router.query("SF", "SF1234567890");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.NOT_CONFIGURED);
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
    }
}
