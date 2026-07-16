package com.colonel.saas.service;

import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import com.colonel.saas.mapper.PerformanceRecordMapper;
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
 * <p>职责：监听业绩计算完成事件（{@link PerformanceCalculatedEvent}），从业绩明细重建受影响日期的
 * 日报汇总表（dashboard_performance_daily），供仪表盘直接读取。
 *
 * <p>聚合策略：
 * <ul>
 *   <li>只读取 {@code performance_records} 的计算结果，不直接消费订单金额</li>
 *   <li>每次覆盖重建受影响日期，保证归因重放、退款和重复事件不会造成累计漂移</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 原始 SQL 执行</li>
 *   <li>{@link PerformanceRecordMapper} —— 读取已计算的业绩事实</li>
 * </ul>
 */
@Service
public class DashboardPerformanceSummaryService {

    /** JDBC 模板，用于执行日报汇总的 UPSERT SQL */
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PerformanceRecordMapper performanceRecordMapper;

    public DashboardPerformanceSummaryService(
            JdbcTemplate jdbcTemplate,
            ApplicationEventPublisher eventPublisher,
            PerformanceRecordMapper performanceRecordMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.performanceRecordMapper = performanceRecordMapper;
    }

    /**
     * 处理业绩计算完成事件，从业绩明细重建日报汇总表。
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>读取业绩记录，不存在时直接跳过</li>
     *   <li>确定统计日期：取订单创建日期，无创建时间时使用当天</li>
     *   <li>按日期从 {@code performance_records} 聚合后 UPSERT 覆盖汇总行</li>
     * </ol>
     *
     * @param event 业绩计算完成事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyPerformanceCalculated(PerformanceCalculatedEvent event) {
        if (event == null || event.orderId() == null || event.orderId().isBlank()) {
            return;
        }
        PerformanceRecord record = performanceRecordMapper.findByOrderId(event.orderId());
        if (record == null) {
            return;
        }
        LocalDate statDate = record.getOrderCreateTime() == null
                ? LocalDate.now()
                : record.getOrderCreateTime().toLocalDate();
        jdbcTemplate.update("""
                INSERT INTO dashboard_performance_daily (
                    stat_date, order_count, order_amount, service_fee_net, updated_at
                )
                SELECT ?,
                       COUNT(*),
                       COALESCE(SUM(COALESCE(pr.pay_amount, 0) + COALESCE(adj.delta_pay_amount, 0)), 0),
                       COALESCE(SUM(COALESCE(pr.effective_service_profit, 0)
                                    + COALESCE(adj.delta_effective_service_profit, 0)), 0),
                       CURRENT_TIMESTAMP
                FROM performance_records pr
                LEFT JOIN (
                    SELECT order_id,
                           SUM(delta_pay_amount) AS delta_pay_amount,
                           SUM(delta_effective_service_profit) AS delta_effective_service_profit
                    FROM performance_adjustment_ledger
                    GROUP BY order_id
                ) adj ON adj.order_id = pr.order_id
                WHERE pr.order_create_time::date = ?
                  AND pr.is_valid = TRUE
                ON CONFLICT (stat_date) DO UPDATE SET
                    order_count = EXCLUDED.order_count,
                    order_amount = EXCLUDED.order_amount,
                    service_fee_net = EXCLUDED.service_fee_net,
                    updated_at = CURRENT_TIMESTAMP
                """,
                statDate,
                statDate);
        eventPublisher.publishEvent(new PerformanceSummaryRefreshedEvent(
                stableEventId(statDate, record.getOrderId()),
                record.getOrderId(),
                statDate,
                "DAY",
                null,
                stableSummaryId(statDate),
                Boolean.TRUE.equals(record.getValid()) ? 1L : 0L,
                nvl(record.getPayAmount()),
                nvl(record.getEffectiveServiceProfit()),
                LocalDateTime.now()));
    }

    @org.springframework.context.event.EventListener
    public void onPerformanceCalculated(PerformanceCalculatedEvent event) {
        applyPerformanceCalculated(event);
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
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
