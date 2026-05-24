package com.colonel.saas.domain.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConfigChangedEventPayload(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID operatorId,
        String operatorName,
        LocalDateTime changedAt,
        String changeReason,
        String source,
        List<ConfigChangedItemPayload> items,
        ConfigChangedImpactPayload impact) {

    public static final String EVENT_TYPE = "CONFIG_CHANGED";
}
