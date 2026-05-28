package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 业绩域配置变更消费者，负责响应业绩/提成相关配置的变更。
 *
 * <p>关注的配置项：
 * <ul>
 *   <li>{@code COMMISSION_BUSINESS_DEFAULT_RATIO} — 业务提成默认比例</li>
 *   <li>{@code COMMISSION_CHANNEL_DEFAULT_RATIO} — 渠道提成默认比例</li>
 *   <li>{@code MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO} — 商家独家服务费比例</li>
 * </ul>
 *
 * <p>当上述任一配置变更时，调用 {@link BusinessRuleConfigService#invalidate}
 * 失效对应的本地缓存，迫使下次业务计算时重新从数据库读取最新配置值。</p>
 */
@Component
public class PerformanceConfigChangedConsumer implements ConfigChangedEventConsumer {

    /** 本消费者关注的配置键集合。 */
    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO,
            SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO,
            SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO);

    private final BusinessRuleConfigService businessRuleConfigService;

    /**
     * 构造函数，注入业务规则配置服务。
     *
     * @param businessRuleConfigService 业务规则配置缓存服务
     */
    public PerformanceConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    /** {@inheritDoc} 消费者名称：{@code performance-config-consumer}。 */
    @Override
    public String consumerName() {
        return "performance-config-consumer";
    }

    /**
     * 判断事件中是否包含本域关心的配置项。
     *
     * @param payload 配置变更事件载荷
     * @return true 表示事件中至少有一个配置项在本域关注列表中
     */
    @Override
    public boolean supports(ConfigChangedEventPayload payload) {
        return payload.items().stream().anyMatch(item -> KEYS.contains(item.configKey()));
    }

    /**
     * 失效事件中涉及的本域配置缓存。
     *
     * @param payload      配置变更事件载荷
     * @param objectMapper JSON 序列化器（本实现未使用）
     */
    @Override
    public void consume(ConfigChangedEventPayload payload, ObjectMapper objectMapper) {
        payload.items().stream()
                .map(ConfigChangedItemPayload::configKey)
                .filter(KEYS::contains)
                .forEach(businessRuleConfigService::invalidate);
    }
}
