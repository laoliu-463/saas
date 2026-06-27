package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.infrastructure.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 达人域配置变更消费者，负责响应达人管理相关配置的变更。
 *
 * <p>关注的配置项：
 * <ul>
 *   <li>{@code TALENT_PROTECTION_DAYS} — 达人保护期天数（防止抢人）</li>
 *   <li>{@code TALENT_EXCLUSIVE_RATIO} — 达人独家合作提成比例</li>
 *   <li>{@code TALENT_EXCLUSIVE_MONTHLY_SAMPLES} — 达人独家月度寄样数量限制</li>
 * </ul>
 *
 * <p>当上述任一配置变更时，调用 {@link BusinessRuleConfigService#invalidate}
 * 失效对应的本地缓存。</p>
 */
@Component
public class TalentConfigChangedConsumer implements ConfigChangedEventConsumer {

    /** 本消费者关注的配置键集合。 */
    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.TALENT_PROTECTION_DAYS,
            SystemConfigKeys.TALENT_EXCLUSIVE_RATIO,
            SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES);

    private final BusinessRuleConfigService businessRuleConfigService;

    /**
     * 构造函数，注入业务规则配置服务。
     *
     * @param businessRuleConfigService 业务规则配置缓存服务
     */
    public TalentConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    /** {@inheritDoc} 消费者名称：{@code talent-config-consumer}。 */
    @Override
    public String consumerName() {
        return "talent-config-consumer";
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
