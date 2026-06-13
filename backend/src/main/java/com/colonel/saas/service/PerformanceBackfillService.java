package com.colonel.saas.service;

import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 业绩记录回填服务，将历史订单批量计算并写入业绩记录表。
 *
 * <p>支持两种回填模式：</p>
 * <ul>
 *   <li><b>全量回填</b>：指定时间范围或订单 ID 列表，全部重新计算</li>
 *   <li><b>仅缺失模式</b>（{@code onlyMissing}）：仅回填 performance_records 中尚不存在的订单</li>
 * </ul>
 *
 * <p><b>业务领域：</b>业绩域 — 数据回填</p>
 * <p><b>协作关系：</b>依赖 {@link PerformanceCalculationService} 执行单笔业绩计算；
 * 依赖 {@link OrderReadFacade} 查询待回填订单</p>
 *
 * @see PerformanceCalculationService
 * @see PerformanceMonthRecalculationService
 */
@Service
public class PerformanceBackfillService {

    /** 默认每批查询订单数 */
    static final int DEFAULT_LIMIT = 200;

    /** 单批最大订单数上限 */
    static final int MAX_LIMIT = 2000;

    private final OrderReadFacade orderReadFacade;

    /** 业绩计算服务，执行单笔订单的业绩 upsert */
    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceBackfillService(
            OrderReadFacade orderReadFacade,
            PerformanceCalculationService performanceCalculationService) {
        this.orderReadFacade = orderReadFacade;
        this.performanceCalculationService = performanceCalculationService;
    }

    /**
     * 重算失效/退款订单上仍为有效业绩的记录。
     * <p>
     * 用于修复订单状态已变为 4/5，但 performance_records 仍为 is_valid=true 的过期数据。
     * </p>
     */
    public BackfillResult reconcileInvalidatedPerformance(Integer limit) {
        List<ColonelsettlementOrder> orders = orderReadFacade.findInvalidatedOrdersWithStalePerformance(
                normalizeLimit(limit));
        int upserted = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (ColonelsettlementOrder order : orders) {
            try {
                if (performanceCalculationService.upsertFromOrder(order) != null) {
                    upserted++;
                }
            } catch (Exception ex) {
                failed++;
                if (errors.size() < 20 && order != null && StringUtils.hasText(order.getOrderId())) {
                    errors.add(order.getOrderId() + ": " + ex.getMessage());
                }
            }
        }
        return new BackfillResult(orders.size(), upserted, failed, false, errors);
    }

    public BackfillResult backfill(
            List<String> orderIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer limit,
            boolean onlyMissing) {
        List<ColonelsettlementOrder> orders = loadOrders(orderIds, startTime, endTime, limit, onlyMissing);
        int upserted = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (ColonelsettlementOrder order : orders) {
            try {
                if (performanceCalculationService.upsertFromOrder(order) != null) {
                    upserted++;
                }
            } catch (Exception ex) {
                failed++;
                if (errors.size() < 20 && order != null && StringUtils.hasText(order.getOrderId())) {
                    errors.add(order.getOrderId() + ": " + ex.getMessage());
                }
            }
        }
        return new BackfillResult(orders.size(), upserted, failed, onlyMissing, errors);
    }

    private List<ColonelsettlementOrder> loadOrders(
            List<String> orderIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer limit,
            boolean onlyMissing) {
        List<String> normalizedOrderIds = normalizeOrderIds(orderIds);
        if (!normalizedOrderIds.isEmpty()) {
            return orderReadFacade.findByOrderIds(normalizedOrderIds);
        }
        return orderReadFacade.findOrdersForBackfill(
                startTime,
                endTime,
                onlyMissing,
                normalizeLimit(limit));
    }

    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public record BackfillResult(
            int scanned,
            int upserted,
            int failed,
            boolean onlyMissing,
            List<String> errors) {
    }
}
