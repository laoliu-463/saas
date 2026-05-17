package com.colonel.saas.gateway.logistics.fallback;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ManualFallbackLogisticsGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(LogisticsGateway.class)
    public LogisticsGateway manualFallbackLogisticsGateway() {
        return new ManualFallbackLogisticsGateway();
    }
}
