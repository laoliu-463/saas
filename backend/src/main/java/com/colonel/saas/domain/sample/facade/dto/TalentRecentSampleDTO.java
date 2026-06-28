package com.colonel.saas.domain.sample.facade.dto;

import java.time.LocalDateTime;

/**
 * Recent sample record exposed to the talent domain without leaking sample persistence details.
 */
public record TalentRecentSampleDTO(
        String sampleRequestId,
        String productName,
        String status,
        String statusText,
        LocalDateTime createTime,
        LocalDateTime completeTime) {
}
