package com.colonel.saas.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩汇总已刷新事件。
 *
 * <p>当前事件由日报汇总刷新路径在写入 {@code dashboard_performance_daily}
 * 成功后发布，供分析 shadow 侧记录刷新事实；不用于反向重算业绩归属。</p>
 *
 * @param eventId             幂等事件 ID
 * @param orderId             触发本次汇总刷新的订单 ID
 * @param statDate            日报统计日期
 * @param period              汇总周期，当前为 DAY
 * @param ownerId             归属 owner；当前全局日报汇总为 null
 * @param summaryId           汇总行稳定 ID
 * @param orderCountDelta     订单数增量
 * @param orderAmountDelta    订单金额增量，单位分
 * @param serviceFeeNetDelta  服务费净收益增量，单位分
 * @param refreshedAt         刷新时间
 */
public record PerformanceSummaryRefreshedEvent(
        UUID eventId,
        String orderId,
        LocalDate statDate,
        String period,
        UUID ownerId,
        UUID summaryId,
        long orderCountDelta,
        long orderAmountDelta,
        long serviceFeeNetDelta,
        LocalDateTime refreshedAt) {
}
