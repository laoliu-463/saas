package com.colonel.saas.domain.analytics.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人认领事件（分析模块可选消费，DDD-ANALYTICS-001）。
 */
public record TalentClaimedEvent(
        UUID eventId,
        UUID claimId,
        String talentUid,
        UUID userId,
        LocalDateTime occurredAt) {
}
