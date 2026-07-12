package com.colonel.saas.domain.order.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 退款/失效订单事实已同步事件。
 */
public record OrderRefundFactSyncedEvent(
        String orderId,
        UUID orderRowId,
        String refundId,
        Long refundAmount,
        Integer previousStatus,
        Integer status,
        String flowPoint,
        Map<String, Object> extraData,
        LocalDateTime occurredAt) {
}
