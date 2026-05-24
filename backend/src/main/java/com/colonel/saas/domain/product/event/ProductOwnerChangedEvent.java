package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品负责人变更。
 */
public record ProductOwnerChangedEvent(
        UUID eventId,
        String activityId,
        String productId,
        UUID oldAssigneeId,
        UUID newAssigneeId,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
