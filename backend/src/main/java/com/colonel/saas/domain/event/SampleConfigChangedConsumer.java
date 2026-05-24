package com.colonel.saas.domain.event;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SampleConfigChangedConsumer implements ConfigChangedEventConsumer {

    private static final Set<String> KEYS = Set.of(
            SystemConfigKeys.SAMPLE_RESTRICT_DAYS,
            SystemConfigKeys.SAMPLE_RESTRICT_ENABLED,
            SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS,
            SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS,
            SystemConfigKeys.SAMPLE_DEFAULT_STANDARD);

    private final BusinessRuleConfigService businessRuleConfigService;

    public SampleConfigChangedConsumer(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Override
    public String consumerName() {
        return "sample-config-consumer";
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
