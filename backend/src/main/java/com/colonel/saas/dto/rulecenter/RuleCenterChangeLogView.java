package com.colonel.saas.dto.rulecenter;

import java.time.LocalDateTime;
import java.util.UUID;

public record RuleCenterChangeLogView(
        UUID id,
        UUID eventId,
        String configKey,
        String changeAction,
        String oldValue,
        String newValue,
        String source,
        String changeReason,
        UUID operatorId,
        Integer configVersion,
        LocalDateTime changedAt) {
}
