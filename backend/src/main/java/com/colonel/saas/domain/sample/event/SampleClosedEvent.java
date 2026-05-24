package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleClosedEvent(
        UUID sampleRequestId,
        String closeReason,
        LocalDateTime closedAt) {
}
