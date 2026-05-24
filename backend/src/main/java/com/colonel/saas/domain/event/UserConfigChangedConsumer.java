package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserConfigChangedConsumer implements ConfigChangedEventConsumer {

    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.LOGIN_MAX_FAILURES,
            SystemConfigKeys.LOGIN_LOCK_MINUTES);

    private final BusinessRuleConfigService businessRuleConfigService;

    public UserConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Override
    public String consumerName() {
        return "user-config-consumer";
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
