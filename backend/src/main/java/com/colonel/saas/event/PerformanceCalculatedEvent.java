package com.colonel.saas.event;

/**
 * 业绩计算完成事件（业绩域 V1.1）。
 * <p>
 * 当业绩服务完成订单的提成/毛利计算后发布此事件。该事件通过 Spring 的
 * {@code ApplicationEventPublisher} 在事务提交后异步发布，供需要感知业绩计算
 * 结果的下游模块（如数据看板汇总、冲正通知等）订阅消费。
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code orderId} — 抖店订单号，作为事件主键</li>
 *   <li>{@code estimateGrossProfit} — 预估毛利（单位：分），基于订单金额和预估费率计算</li>
 *   <li>{@code effectiveGrossProfit} — 实际毛利（单位：分），基于结算金额和实际费率计算</li>
 *   <li>{@code reversed} — 是否为冲正记录，{@code true} 表示此事件为反向冲正</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.listener.PerformanceRecordSyncListener
 */
public record PerformanceCalculatedEvent(
        /** 抖店订单号 */
        String orderId,
        /** 预估毛利，单位：分 */
        long estimateGrossProfit,
        /** 实际毛利，单位：分 */
        long effectiveGrossProfit,
        /** 是否为冲正记录，true 表示此事件为反向冲正 */
        boolean reversed) {
}
