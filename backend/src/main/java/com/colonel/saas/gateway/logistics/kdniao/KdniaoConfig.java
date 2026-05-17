package com.colonel.saas.gateway.logistics.kdniao;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kdniao")
public class KdniaoConfig {

    private String baseUrl = "https://api.kdniao.com";
    private String sandboxUrl = "http://sandboxapi.kdniao.com:8080/kdniaosandbox/gateway/exterfaceInvoke.json";
    private String requestUrl;
    private String requestType = "1002";
    private String eBusinessId;
    private String appKey;
    private boolean sandbox = false;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);

    @Bean
    public RestTemplate kdniaoRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    public String getRequestUrl() {
        if (StringUtils.hasText(requestUrl)) {
            return requestUrl;
        }
        return sandbox ? sandboxUrl : baseUrl + "/Ebusiness/EbusinessOrderHandle.aspx";
    }
}
