package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleCompletedEvent(
        UUID sampleRequestId,
        String orderId,
        UUID productId,
        UUID talentId,
        UUID channelId,
        LocalDateTime completedAt) {
}
