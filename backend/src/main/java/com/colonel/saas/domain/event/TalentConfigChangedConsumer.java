package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TalentConfigChangedConsumer implements ConfigChangedEventConsumer {

    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.TALENT_PROTECTION_DAYS,
            SystemConfigKeys.TALENT_EXCLUSIVE_RATIO,
            SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES);

    private final BusinessRuleConfigService businessRuleConfigService;

    public TalentConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Override
    public String consumerName() {
        return "talent-config-consumer";
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
