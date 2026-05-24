package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleSignedEvent(
        UUID sampleRequestId,
        String trackingNo,
        LocalDateTime signedAt) {
}
