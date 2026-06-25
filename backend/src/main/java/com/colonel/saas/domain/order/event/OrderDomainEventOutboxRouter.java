package com.colonel.saas.domain.order.event;

import com.colonel.saas.domain.event.DomainEventOutbox;
import org.springframework.stereotype.Component;

/**
 * 订单域 Outbox 事件路由器（DDD-OUTBOX-001）。
 */
@Component
public class OrderDomainEventOutboxRouter {

    private final OrderDomainEventPublisher orderDomainEventPublisher;

    public OrderDomainEventOutboxRouter(OrderDomainEventPublisher orderDomainEventPublisher) {
        this.orderDomainEventPublisher = orderDomainEventPublisher;
    }

    public boolean supports(String eventType) {
        return eventType != null && eventType.startsWith("Order");
    }

    public void dispatch(DomainEventOutbox event) {
        orderDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload());
    }
}
