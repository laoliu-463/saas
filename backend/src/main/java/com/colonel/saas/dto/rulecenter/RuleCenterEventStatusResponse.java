package com.colonel.saas.dto.rulecenter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RuleCenterEventStatusResponse(
        UUID eventId,
        String eventType,
        String status,
        Integer retryCount,
        String errorMessage,
        LocalDateTime occurredAt,
        LocalDateTime publishedAt,
        List<ConsumerStatusView> consumers) {

    public record ConsumerStatusView(
            String consumerName,
            String status,
            String errorMessage,
            LocalDateTime consumedAt) {
    }
}
