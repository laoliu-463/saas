package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleRejectedEvent(
        UUID sampleRequestId,
        UUID rejectedBy,
        String rejectedReason,
        LocalDateTime rejectedAt) {
}
