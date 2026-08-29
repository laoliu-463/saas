package com.colonel.saas.domain.order.event;

import com.colonel.saas.event.OrderSyncedEvent;

/**
 * 订单域事件发布接口（DDD-ORDER-005）。
 */
public interface OrderDomainEventPublisher {
    boolean isOutboxRoutingEnabled();
    void appendOrderSyncedInTransaction(String eventKey, OrderSyncedEvent event);
    void appendOrderAttributionReplayedInTransaction(String eventKey, OrderAttributionReplayedEvent event);
    void appendOrderRefundFactSyncedInTransaction(String eventKey, OrderRefundFactSyncedEvent event);
    void publishOrderSynced(OrderSyncedEvent event);
    void publishOrderAttributionReplayed(OrderAttributionReplayedEvent event);
    void publishOrderSyncedDirect(OrderSyncedEvent event);
    void publishOrderAttributionReplayedDirect(OrderAttributionReplayedEvent event);
    void publishOrderRefundFactSynced(OrderRefundFactSyncedEvent event);
    void publishOrderRefundFactSyncedDirect(OrderRefundFactSyncedEvent event);
    void publishOrderStatusChangedDirect(OrderStatusChangedEvent event);
    void republishSpringEvent(String eventType, String payloadJson);
}
