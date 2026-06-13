package com.colonel.saas.service;

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
 * 业绩月度重算服务，针对未结算订单重新计算业绩记录。
 *
 * <p>每月定时或手动触发，扫描指定月份内未结算的订单，
 * 逐笔调用 {@link PerformanceCalculationService#upsertFromOrder} 重新计算业绩。</p>
 */
@Service
public class PerformanceMonthRecalculationService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final int MAX_RECALC_ORDERS = 2000;

    private final OrderReadFacade orderReadFacade;

    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceMonthRecalculationService(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationService performanceCalculationService) {
        this.orderReadFacade = orderReadFacade;
        this.performanceCalculationService = performanceCalculationService;
    }

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
                if (performanceCalculationService.upsertFromOrder(order) != null) {
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
