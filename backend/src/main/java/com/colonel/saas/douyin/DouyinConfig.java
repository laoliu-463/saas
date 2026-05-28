package com.colonel.saas.douyin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 抖音开放平台应用配置。
 * <p>
 * 从 application.yml 的 {@code douyin.app.*} 前缀读取配置属性，
 * 包含 API 基础 URL、应用凭据和 HTTP 客户端超时设置。
 *
 * <ul>
 *   <li>API 基础 URL — 抖音开放平台请求入口地址</li>
 *   <li>应用凭据 — appId / clientKey / clientSecret 用于签名和鉴权</li>
 *   <li>沙箱模式 — 控制是否使用沙箱环境</li>
 *   <li>HTTP 超时 — 配置 RestTemplate 的连接和读取超时</li>
 * </ul>
 *
 * 所属业务领域：抖音开放平台 / 基础设施配置
 *
 * @see DouyinApiClient
 * @see DoudianTokenGateway
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "douyin.app")
public class DouyinConfig {

    private String baseUrl = "https://openapi-fxg.jinritemai.com";
    private String appId;
    private String clientKey;
    private String clientSecret;
    private boolean sandbox;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 创建抖音 API 专用的 RestTemplate Bean。
     * <p>
     * 使用 {@link DouyinConfig} 中配置的连接超时和读取超时初始化，
     * 供 {@link DouyinApiClient} 进行 HTTP 调用。
     *
     * @param restTemplateBuilder Spring Boot 自动注入的 RestTemplate 构建器
     * @return 配置好超时的 RestTemplate 实例
     */
    @Bean
    public RestTemplate douyinRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
