package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleCreatedEvent(
        UUID sampleRequestId,
        UUID productId,
        String productName,
        UUID talentId,
        String talentName,
        UUID channelId,
        String channelName,
        UUID recruiterId,
        String partnerId,
        Integer status,
        LocalDateTime createdAt) {
}
