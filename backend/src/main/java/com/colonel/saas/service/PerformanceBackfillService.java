package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
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
 * 依赖 {@link ColonelsettlementOrderMapper} 查询待回填订单</p>
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

    /** 订单 Mapper，查询待回填的订单列表 */
    private final ColonelsettlementOrderMapper orderMapper;

    /** 业绩计算服务，执行单笔订单的业绩 upsert */
    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceBackfillService(
            ColonelsettlementOrderMapper orderMapper,
            PerformanceCalculationService performanceCalculationService) {
        this.orderMapper = orderMapper;
        this.performanceCalculationService = performanceCalculationService;
    }

    /**
     * 执行业绩记录批量回填。
     *
     * <ol>
     *   <li>第一步：根据订单 ID 列表或时间范围加载待回填订单</li>
     *   <li>第二步：逐笔调用 {@link PerformanceCalculationService#upsertFromOrder} 计算并写入业绩</li>
     *   <li>第三步：统计结果（扫描数、成功数、失败数），收集最多 20 条错误信息</li>
     * </ol>
     *
     * @param orderIds   指定订单 ID 列表（优先级高于时间范围），为空则按时间范围查询
     * @param startTime  起始时间（基于 settleTime），可为 null
     * @param endTime    结束时间（基于 settleTime），可为 null
     * @param limit      每批查询上限，超过 {@value MAX_LIMIT} 会被截断
     * @param onlyMissing 是否仅回填缺失的业绩记录
     * @return 回填结果，包含统计数字和错误列表
     */
    /**
     * 重算失效/退款订单上仍为有效业绩的记录。
     * <p>
     * 用于修复订单状态已变为 4/5，但 performance_records 仍为 is_valid=true 的过期数据。
     * </p>
     */
    public BackfillResult reconcileInvalidatedPerformance(Integer limit) {
        List<ColonelsettlementOrder> orders = loadInvalidatedWithStalePerformance(limit);
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
        // 第一步：加载待回填订单
        List<ColonelsettlementOrder> orders = loadOrders(orderIds, startTime, endTime, limit, onlyMissing);
        // 第二步：逐笔计算并统计
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
                // 注意：最多收集 20 条错误信息
                if (errors.size() < 20 && order != null && StringUtils.hasText(order.getOrderId())) {
                    errors.add(order.getOrderId() + ": " + ex.getMessage());
                }
            }
        }
        // 第三步：构建返回结果
        return new BackfillResult(orders.size(), upserted, failed, onlyMissing, errors);
    }

    /**
     * 加载待回填订单列表，支持按订单 ID 或时间范围查询。
     *
     * <ol>
     *   <li>第一步：若提供了订单 ID 列表，直接按 ID 查询</li>
     *   <li>第二步：否则按时间范围查询，若 onlyMissing 为 true 则附加 NOT EXISTS 条件</li>
     *   <li>第三步：按创建时间倒序排列，限制返回数量</li>
     * </ol>
     */
    private List<ColonelsettlementOrder> loadInvalidatedWithStalePerformance(Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .in(ColonelsettlementOrder::getOrderStatus,
                        OrderCommissionPolicy.STATUS_CANCELLED,
                        OrderCommissionPolicy.STATUS_REFUNDED)
                .apply("""
                        EXISTS (
                            SELECT 1 FROM performance_records pr
                            WHERE pr.order_id = colonelsettlement_order.order_id
                              AND pr.is_valid = TRUE
                        )
                        """)
                .orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .last("LIMIT " + safeLimit));
    }

    private List<ColonelsettlementOrder> loadOrders(
            List<String> orderIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer limit,
            boolean onlyMissing) {
        // 第一步：若提供了订单 ID 列表，优先使用
        List<String> normalizedOrderIds = normalizeOrderIds(orderIds);
        if (!normalizedOrderIds.isEmpty()) {
            return orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                    .in(ColonelsettlementOrder::getOrderId, normalizedOrderIds)
                    .eq(ColonelsettlementOrder::getDeleted, 0));
        }

        // 第二步：按时间范围查询，附加 onlyMissing 条件
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0);
        if (onlyMissing) {
            // 注意：NOT EXISTS 子查询排除已有业绩记录的订单
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
        // 第三步：按创建时间倒序，限制数量
        wrapper.orderByDesc(ColonelsettlementOrder::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return orderMapper.selectList(wrapper);
    }

    /**
     * 规范化订单 ID 列表：去空、去重、去空白。
     */
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

    /**
     * 规范化查询数量限制，默认 {@value DEFAULT_LIMIT}，上限 {@value MAX_LIMIT}。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 回填结果记录。
     *
     * @param scanned    扫描的订单总数
     * @param upserted   成功写入/更新的业绩记录数
     * @param failed     失败的订单数
     * @param onlyMissing 是否为仅缺失模式
     * @param errors     错误信息列表（最多 20 条）
     */
    public record BackfillResult(
            int scanned,
            int upserted,
            int failed,
            boolean onlyMissing,
            List<String> errors) {
    }
}
