package com.colonel.saas.domain.order.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单状态变更事件（DDD-ORDER-005）。
 */
public record OrderStatusChangedEvent(
        String orderId,
        UUID orderRowId,
        Integer previousStatus,
        Integer currentStatus,
        boolean newlyInserted,
        String attributionStatus,
        LocalDateTime occurredAt) {
}
