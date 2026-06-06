package com.colonel.saas.service;

import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 仪表盘业绩日报汇总服务。
 *
 * <p>职责：监听订单同步事件（{@link OrderSyncedEvent}），将有效订单的业绩指标
 * 实时聚合到日报汇总表（dashboard_performance_daily），供仪表盘直接读取。
 *
 * <p>聚合策略：
 * <ul>
 *   <li>仅处理新增插入事件（{@code newlyInserted=true}）且订单状态计入业绩的事件</li>
 *   <li>服务费净收益使用结算服务费收入；该收入已按结算订单额 × 服务费率 - 技术服务费形成</li>
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

    public DashboardPerformanceSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 处理订单同步事件，将业绩指标累加到日报汇总表。
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>过滤：非新增插入或不计入业绩的订单状态，直接跳过</li>
     *   <li>读取结算服务费收入作为服务费净收益（取下限 0）</li>
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
        long serviceFeeNet = Math.max(event.effectiveServiceFee(), 0L);
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
                Math.max(event.orderAmount(), 0L),
                serviceFeeNet);
    }
}
