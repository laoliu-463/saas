package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * 业绩月度重算应用服务（DDD-PERFORMANCE Slice 5）。
 *
 * <p>从 {@code service.PerformanceMonthRecalculationService} 整体迁移过来的月度重算逻辑：
 * <ul>
 *   <li>{@link #recalculateMonth} - 扫描指定月份未结算订单，逐笔委派
 *       {@link PerformanceCalculationApplicationService#upsertFromOrder} 重算业绩</li>
 * </ul>
 *
 * <p>本类是 performance 域月度重算的独立入口。承接原 Service 的所有逻辑
 * （month 解析 / 时间区间计算 / 单条失败容错 / 响应组装）。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link OrderReadFacade} —— 订单只读 facade（按时间范围查未结算订单）</li>
 *   <li>{@link PerformanceCalculationApplicationService} —— 业绩计算 application（单订单 upsert）</li>
 * </ul>
 */
@Service
public class PerformanceMonthRecalculationApplicationService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final int MAX_RECALC_ORDERS = 2000;

    private final OrderReadFacade orderReadFacade;

    private final PerformanceCalculationApplicationService performanceCalculationApplicationService;

    public PerformanceMonthRecalculationApplicationService(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationApplicationService performanceCalculationApplicationService) {
        this.orderReadFacade = orderReadFacade;
        this.performanceCalculationApplicationService = performanceCalculationApplicationService;
    }

    /**
     * 月度业绩重算。每月定时或手动触发，扫描指定月份内未结算的订单，
     * 逐笔调用 {@link PerformanceCalculationApplicationService#upsertFromOrder} 重新计算业绩。
     *
     * @param month  目标月份（格式 yyyy-MM）
     * @param reason 重算原因
     * @return 重算响应（jobId / status / scanned / upserted / skippedSettled）
     */
    public PerformanceRecalculateMonthResponse recalculateMonth(String month, String reason) {
        if (!StringUtils.hasText(month)) {
            throw BusinessException.param("month 不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw BusinessException.param("reason 不能为空");
        }
        YearMonth targetMonth;
        try {
            targetMonth = YearMonth.parse(month.trim(), MONTH);
        } catch (DateTimeParseException ex) {
            throw BusinessException.param("month 格式应为 yyyy-MM");
        }

        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<ColonelsettlementOrder> orders = orderReadFacade.findUnsettledOrdersByCreateTimeRange(
                start, end, MAX_RECALC_ORDERS);

        int upserted = 0;
        int skippedSettled = 0;
        for (ColonelsettlementOrder order : orders) {
            if (order.getSettleTime() != null) {
                skippedSettled++;
                continue;
            }
            try {
                if (performanceCalculationApplicationService.upsertFromOrder(order) != null) {
                    upserted++;
                }
            } catch (Exception ignored) {
                // 单条失败不影响整批重算
            }
        }

        PerformanceRecalculateMonthResponse response = new PerformanceRecalculateMonthResponse();
        response.setJobId(UUID.randomUUID().toString());
        response.setStatus("SUBMITTED");
        response.setMonth(targetMonth.toString());
        response.setScanned(orders.size());
        response.setUpserted(upserted);
        response.setSkippedSettled(skippedSettled);
        return response;
    }
}