package com.colonel.saas.constant;

/**
 * 订单域 Outbox 事件类型常量（DDD-OUTBOX-001）。
 */
public final class OrderDomainEventTypes {

    /** 订单同步完成事件，对应 {@link com.colonel.saas.event.OrderSyncedEvent}。 */
    public static final String ORDER_SYNCED = "OrderSynced";
    /** 受控归因重放已更正订单归因事实。 */
    public static final String ORDER_ATTRIBUTION_REPLAYED = "OrderAttributionReplayed";
    /** 退款/失效订单事实已同步事件。 */
    public static final String ORDER_REFUND_FACT_SYNCED = "OrderRefundFactSynced";

    private OrderDomainEventTypes() {
    }
}
