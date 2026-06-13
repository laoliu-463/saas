package com.colonel.saas.domain.order.event;

import com.colonel.saas.event.OrderSyncedEvent;

/**
 * 订单域事件发布接口（DDD-ORDER-005）。
 */
public interface OrderDomainEventPublisher {
    boolean isOutboxRoutingEnabled();
    void appendOrderSyncedInTransaction(String eventKey, OrderSyncedEvent event);
    void publishOrderSynced(OrderSyncedEvent event);
    void publishOrderSyncedDirect(OrderSyncedEvent event);
    void publishOrderStatusChangedDirect(OrderStatusChangedEvent event);
    void republishSpringEvent(String eventType, String payloadJson);
}
