package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleApprovedEvent(
        UUID sampleRequestId,
        UUID productId,
        UUID talentId,
        UUID channelId,
        UUID recruiterId,
        UUID approvedBy,
        LocalDateTime approvedAt) {
}
