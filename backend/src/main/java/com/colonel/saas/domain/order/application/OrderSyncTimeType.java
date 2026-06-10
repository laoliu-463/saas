package com.colonel.saas.domain.order.application;

/**
 * 抖音 colonelMultiSettlementOrders 的 time_type（DDD-ORDER-001）。
 * <p>当前事实：仅支持 {@code update} 与 {@code settle}；不存在 {@code pay} 通道。</p>
 */
public enum OrderSyncTimeType {
    UPDATE,
    SETTLE
}
