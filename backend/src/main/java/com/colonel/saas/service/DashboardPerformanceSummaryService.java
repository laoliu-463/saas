package com.colonel.saas.service;

import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DashboardPerformanceSummaryService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardPerformanceSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyOrderSynced(OrderSyncedEvent event) {
        if (event == null
                || !event.newlyInserted()
                || !OrderCommissionPolicy.countsTowardPerformance(event.orderStatus())) {
            return;
        }
        long serviceFeeNet = Math.max(
                event.settleColonelCommission()
                        - event.settleColonelTechServiceFee()
                        - event.settleSecondColonelCommission(),
                0L);
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
