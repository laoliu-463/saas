package com.colonel.saas.gateway.logistics.kdniao;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 快递鸟物流服务商配置类。
 *
 * <p>功能描述：读取 {@code kdniao.*} 前缀的配置项，提供快递鸟 API 的连接参数与认证凭据。
 * 同时创建 {@link RestTemplate} Bean 供 {@link KdniaoLogisticsGateway} 注入使用。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>仅当 {@code kdniao.enabled=true} 时激活此配置（{@code @ConditionalOnProperty}）</li>
 *   <li>{@code sandbox=true} 时使用沙箱地址（sandboxapi.kdniao.com:8080），用于测试联调</li>
 *   <li>{@code sandbox=false}（默认）时使用生产地址（api.kdniao.com）</li>
 *   <li>可通过 {@code requestUrl} 直接指定完整请求地址，覆盖以上逻辑</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：寄样域 / 快递鸟物流适配层</p>
 *
 * @see KdniaoLogisticsGateway
 */
@Data
@Configuration
@ConditionalOnProperty(name = "kdniao.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "kdniao")
public class KdniaoConfig {

    /** 快递鸟生产 API 基础地址 */
    private String baseUrl = "https://api.kdniao.com";

    /** 快递鸟沙箱 API 地址（用于测试环境） */
    private String sandboxUrl = "http://sandboxapi.kdniao.com:8080/kdniaosandbox/gateway/exterfaceInvoke.json";

    /** 自定义完整请求地址（优先级最高，配置后覆盖 baseUrl/sandboxUrl 的拼接逻辑） */
    private String requestUrl;

    /** 快递鸟请求类型：1002 表示即时查询（非订阅） */
    private String requestType = "1002";

    /** 快递鸟商户 ID（EBusinessID），用于 API 签名认证 */
    private String eBusinessId;

    /** 快递鸟 API 密钥（AppKey），用于生成请求签名 */
    private String appKey;

    /** 是否启用沙箱模式（true=沙箱，false=生产），默认 false */
    private boolean sandbox = false;

    /** HTTP 连接超时时间 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** HTTP 读取超时时间 */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * 创建快递鸟专用的 RestTemplate Bean。
     *
     * @param restTemplateBuilder Spring RestTemplate 构建器
     * @return 配置了超时时间的 RestTemplate 实例
     */
    @Bean
    public RestTemplate kdniaoRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    /**
     * 获取实际请求地址。
     *
     * <p>优先级：requestUrl（自定义）> sandbox ? sandboxUrl : baseUrl + 默认路径</p>
     *
     * @return 快递鸟 API 完整请求 URL
     */
    public String getRequestUrl() {
        if (StringUtils.hasText(requestUrl)) {
            return requestUrl;
        }
        return sandbox ? sandboxUrl : baseUrl + "/Ebusiness/EbusinessOrderHandle.aspx";
    }
}
