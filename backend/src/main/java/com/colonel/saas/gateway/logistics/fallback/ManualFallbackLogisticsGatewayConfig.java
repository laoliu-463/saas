package com.colonel.saas.gateway.logistics.fallback;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 手动回退物流网关自动装配配置。
 *
 * <p>功能描述：当 Spring 容器中不存在任何其他 {@link LogisticsGateway} Bean 时
 * （即快递鸟、快递100等服务商均未启用），自动注册 {@link ManualFallbackLogisticsGateway}
 * 作为兜底实现，确保物流网关始终可用。</p>
 *
 * <p>环境说明：使用 {@code @ConditionalOnMissingBean} 保证优先级最低，
 * 只要有任何其他物流实现被注册，此配置不会生效。</p>
 *
 * <p>所属业务领域：寄样域 / 物流适配层自动装配</p>
 *
 * @see ManualFallbackLogisticsGateway
 * @see LogisticsGateway
 */
@Configuration(proxyBeanMethods = false)
public class ManualFallbackLogisticsGatewayConfig {

    /**
     * 注册手动回退物流网关 Bean（仅当容器中无其他 {@link LogisticsGateway} 实现时生效）。
     *
     * @return {@link ManualFallbackLogisticsGateway} 实例
     */
    @Bean
    @ConditionalOnMissingBean(LogisticsGateway.class)
    public LogisticsGateway manualFallbackLogisticsGateway() {
        return new ManualFallbackLogisticsGateway();
    }
}
