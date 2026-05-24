package com.colonel.saas.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户已禁用（离职/停用）。
 */
public record UserDisabledEvent(
        UUID eventId,
        UUID userId,
        String oldStatus,
        String newStatus,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
