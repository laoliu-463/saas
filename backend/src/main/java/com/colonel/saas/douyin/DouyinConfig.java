package com.colonel.saas.douyin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "douyin.app")
public class DouyinConfig {

    private String baseUrl = "https://open.douyin.com";
    private String appId;
    private String clientKey;
    private String clientSecret;
    private boolean sandbox;

    @Bean
    public RestTemplate douyinRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
