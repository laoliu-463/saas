package com.colonel.saas.domain.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品上架（加入商品库并参与展示竞争）。
 */
public record ProductListedEvent(
        UUID eventId,
        String activityId,
        String productId,
        UUID operationStateId,
        UUID operatorId,
        LocalDateTime occurredAt,
        String traceId) {
}
