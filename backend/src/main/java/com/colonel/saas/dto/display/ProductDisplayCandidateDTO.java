package com.colonel.saas.dto.display;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductDisplayCandidateDTO(
        UUID relationId,
        String activityId,
        String activityName,
        String partnerName,
        String recruiterName,
        BigDecimal commissionRate,
        BigDecimal serviceFeeRate,
        Boolean supportsAds,
        String displayStatus,
        String hiddenReason,
        LocalDateTime firstDisplayedAt,
        LocalDateTime lastDisplayedAt) {
}
