package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品从商品库展示中隐藏。
 */
public record ProductHiddenEvent(
        UUID eventId,
        String activityId,
        String productId,
        UUID operationStateId,
        String reason,
        LocalDateTime occurredAt,
        String traceId) {
}
