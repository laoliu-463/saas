package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PerformanceConfigChangedConsumer implements ConfigChangedEventConsumer {

    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO,
            SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO,
            SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO);

    private final BusinessRuleConfigService businessRuleConfigService;

    public PerformanceConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Override
    public String consumerName() {
        return "performance-config-consumer";
    }

    @Override
    public boolean supports(ConfigChangedEventPayload payload) {
        return payload.items().stream().anyMatch(item -> KEYS.contains(item.configKey()));
    }

    @Override
    public void consume(ConfigChangedEventPayload payload, ObjectMapper objectMapper) {
        payload.items().stream()
                .map(ConfigChangedItemPayload::configKey)
                .filter(KEYS::contains)
                .forEach(businessRuleConfigService::invalidate);
    }
}
