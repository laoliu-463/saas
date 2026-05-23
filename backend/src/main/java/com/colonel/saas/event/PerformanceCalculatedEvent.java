package com.colonel.saas.event;

/**
 * 业绩计算完成事件（业绩域 V1.1）。
 */
public record PerformanceCalculatedEvent(
        String orderId,
        long estimateGrossProfit,
        long effectiveGrossProfit,
        boolean reversed) {
}
