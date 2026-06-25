package com.colonel.saas.constant;

/**
 * 订单域 Outbox 事件类型常量（DDD-OUTBOX-001）。
 */
public final class OrderDomainEventTypes {

    /** 订单同步完成事件，对应 {@link com.colonel.saas.event.OrderSyncedEvent}。 */
    public static final String ORDER_SYNCED = "OrderSynced";

    private OrderDomainEventTypes() {
    }
}
