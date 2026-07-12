package com.colonel.saas.service;

import com.colonel.saas.domain.performance.policy.PerformanceMoneyPolicy;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 仪表盘业绩日报汇总服务。
 *
 * <p>职责：监听订单同步事件（{@link OrderSyncedEvent}），将有效订单的业绩指标
 * 实时聚合到日报汇总表（dashboard_performance_daily），供仪表盘直接读取。
 *
 * <p>聚合策略：
 * <ul>
 *   <li>仅处理新增插入事件（{@code newlyInserted=true}）且订单状态计入业绩的事件</li>
 *   <li>服务费净收益使用结算服务费收入扣除结算服务费支出后的结果</li>
 *   <li>使用 PostgreSQL {@code ON CONFLICT ... DO UPDATE} 实现按日期幂等累加</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 原始 SQL 执行</li>
 *   <li>{@link OrderCommissionPolicy} —— 订单状态判定（是否计入业绩）</li>
 * </ul>
 */
@Service
public class DashboardPerformanceSummaryService {

    /** JDBC 模板，用于执行日报汇总的 UPSERT SQL */
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public DashboardPerformanceSummaryService(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理订单同步事件，将业绩指标累加到日报汇总表。
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>过滤：非新增插入或不计入业绩的订单状态，直接跳过</li>
     *   <li>读取结算服务费收入与结算服务费支出，按业绩域金额策略计算净收益</li>
     *   <li>确定统计日期：取订单创建日期，无创建时间时使用当天</li>
     *   <li>UPSERT 到 dashboard_performance_daily 表：已存在则累加，不存在则插入新行</li>
     * </ol>
     *
     * @param event 订单同步事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyOrderSynced(OrderSyncedEvent event) {
        if (event == null
                || !event.newlyInserted()
                || !OrderCommissionPolicy.countsTowardPerformance(event.orderStatus())) {
            return;
        }
        long orderAmountDelta = Math.max(event.orderAmount(), 0L);
        long serviceFeeNet = PerformanceMoneyPolicy.serviceFeeNetCent(
                event.effectiveServiceFee(),
                0L,
                event.effectiveServiceFeeExpense());
        LocalDate statDate = event.orderCreateTime() == null
                ? LocalDate.now()
                : event.orderCreateTime().toLocalDate();
        jdbcTemplate.update("""
                INSERT INTO dashboard_performance_daily (
                    stat_date, order_count, order_amount, service_fee_net, updated_at
                ) VALUES (?, 1, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (stat_date) DO UPDATE SET
                    order_count = dashboard_performance_daily.order_count + 1,
                    order_amount = dashboard_performance_daily.order_amount + EXCLUDED.order_amount,
                    service_fee_net = dashboard_performance_daily.service_fee_net + EXCLUDED.service_fee_net,
                    updated_at = CURRENT_TIMESTAMP
                """,
                statDate,
                orderAmountDelta,
                serviceFeeNet);
        eventPublisher.publishEvent(new PerformanceSummaryRefreshedEvent(
                stableEventId(statDate, event.orderId()),
                event.orderId(),
                statDate,
                "DAY",
                null,
                stableSummaryId(statDate),
                1L,
                orderAmountDelta,
                serviceFeeNet,
                LocalDateTime.now()));
    }

    private static UUID stableEventId(LocalDate statDate, String orderId) {
        return UUID.nameUUIDFromBytes(
                ("PerformanceSummaryRefreshed:" + statDate + ":" + orderId)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static UUID stableSummaryId(LocalDate statDate) {
        return UUID.nameUUIDFromBytes(
                ("DashboardPerformanceDaily:" + statDate)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
