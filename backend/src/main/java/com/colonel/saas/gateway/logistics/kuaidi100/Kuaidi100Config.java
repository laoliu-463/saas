package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.config.LogisticsProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 快递100物流服务商 HTTP 客户端配置。
 *
 * <p>功能描述：创建快递100专用的 {@link RestTemplate} Bean，超时时间从
 * {@code logistics.query.timeout-seconds} 配置项读取（最低 1 秒）。
 * 业务配置统一使用 {@code logistics.kd100.*} 前缀。</p>
 *
 * <p>环境说明：此配置始终生效（无 @ConditionalOnProperty），由
 * {@link com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway}
 * 根据自身条件决定是否激活。</p>
 *
 * <p>所属业务领域：寄样域 / 快递100物流适配层</p>
 *
 * @see Kuaidi100LogisticsGateway
 * @see com.colonel.saas.config.LogisticsProperties
 */
@Configuration
public class Kuaidi100Config {

    /**
     * 创建快递100专用的 RestTemplate Bean。
     *
     * @param restTemplateBuilder   Spring RestTemplate 构建器
     * @param logisticsProperties   物流配置属性（包含超时时间等）
     * @return 配置了超时时间的 RestTemplate 实例（连接超时与读取超时使用同一值）
     */
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
