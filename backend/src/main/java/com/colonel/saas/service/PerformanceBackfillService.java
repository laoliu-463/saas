package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PerformanceBackfillService {

    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 2000;

    private final ColonelsettlementOrderMapper orderMapper;
    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceBackfillService(
            ColonelsettlementOrderMapper orderMapper,
            PerformanceCalculationService performanceCalculationService) {
        this.orderMapper = orderMapper;
        this.performanceCalculationService = performanceCalculationService;
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
            return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                    .in(ColonelsettlementOrder::getOrderId, normalizedOrderIds)
                    .eq(ColonelsettlementOrder::getDeleted, 0));
        }

        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0);
        if (onlyMissing) {
            wrapper.apply("""
                    NOT EXISTS (
                        SELECT 1 FROM performance_records pr
                        WHERE pr.order_id = colonelsettlement_order.order_id
                    )
                    """);
        }
        if (startTime != null) {
            wrapper.ge(ColonelsettlementOrder::getSettleTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(ColonelsettlementOrder::getSettleTime, endTime);
        }
        wrapper.orderByDesc(ColonelsettlementOrder::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return orderMapper.selectList(wrapper);
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
