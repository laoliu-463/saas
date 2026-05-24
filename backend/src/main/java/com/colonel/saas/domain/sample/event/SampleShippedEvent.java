package com.colonel.saas.domain.sample.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SampleShippedEvent(
        UUID sampleRequestId,
        String logisticsCompany,
        String trackingNo,
        UUID shippedBy,
        LocalDateTime shippedAt) {
}
