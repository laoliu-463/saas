package com.colonel.saas.gateway.logistics.kuaidi100;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 快递100配置
 * 文档：https://www.kuaidi100.com/openapi/
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kuaidi100")
public class Kuaidi100Config {

    private String requestUrl = "https://poll.kuaidi100.com/poll/query";
    private String customer;
    private String secret;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);

    @Bean
    public RestTemplate kuaidi100RestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
