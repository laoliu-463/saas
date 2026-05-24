package com.colonel.saas.config;

import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangedEventFactoryTest {

    private final ConfigChangedEventFactory factory = new ConfigChangedEventFactory(
            new RuleCenterSchemaRegistry(new ConfigDefinitionRegistry(new com.fasterxml.jackson.databind.ObjectMapper())));

    @Test
    void create_shouldMarkCommissionChangeAsManualRecalculate() {
        UUID eventId = UUID.randomUUID();
        ConfigChangedEventPayload payload = factory.create(
                eventId,
                UUID.randomUUID(),
                "admin",
                "调整提成",
                "RULE_CENTER",
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO,
                        "0.15",
                        "0.20",
                        2)));

        assertThat(payload.eventType()).isEqualTo(ConfigChangedEventPayload.EVENT_TYPE);
        assertThat(payload.impact().needManualRecalculate()).isTrue();
        assertThat(payload.items()).singleElement()
                .extracting(item -> item.configKey())
                .isEqualTo(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO);
    }
}
