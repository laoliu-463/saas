package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.config.LogisticsProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 快递100 HTTP 客户端配置。业务配置统一使用 logistics.kd100.*。
 */
@Configuration
public class Kuaidi100Config {

    @Bean
    public RestTemplate kuaidi100RestTemplate(
            RestTemplateBuilder restTemplateBuilder,
            LogisticsProperties logisticsProperties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, logisticsProperties.getQuery().getTimeoutSeconds()));
        return restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
