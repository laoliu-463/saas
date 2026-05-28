package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
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
 *
 * <ul>
 *   <li>提供 {@link #recalculateMonth} 重算指定月份的未结算订单业绩</li>
 *   <li>单次最大重算 {@value MAX_RECALC_ORDERS} 笔订单，防止大批量超时</li>
 * </ul>
 *
 * <p><b>业务领域：</b>业绩域 — 月度重算</p>
 * <p><b>协作关系：</b>依赖 {@link PerformanceCalculationService} 执行单笔业绩计算；
 * 依赖 {@link ColonelsettlementOrderMapper} 查询目标订单</p>
 *
 * @see PerformanceCalculationService
 * @see PerformanceBackfillService
 */
@Service
public class PerformanceMonthRecalculationService {

    /** 月份格式化器，解析 "yyyy-MM" 格式的月份字符串 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 单次重算最大订单数，防止大批量操作超时 */
    private static final int MAX_RECALC_ORDERS = 2000;

    /** 订单 Mapper，查询指定月份的未结算订单 */
    private final ColonelsettlementOrderMapper orderMapper;

    /** 业绩计算服务，执行单笔订单的业绩 upsert */
    private final PerformanceCalculationService performanceCalculationService;

    public PerformanceMonthRecalculationService(
            ColonelsettlementOrderMapper orderMapper,
            PerformanceCalculationService performanceCalculationService) {
        this.orderMapper = orderMapper;
        this.performanceCalculationService = performanceCalculationService;
    }

    /**
     * 重算指定月份内未结算订单的业绩记录。
     *
     * <ol>
     *   <li>第一步：校验 month 和 reason 参数，解析为 {@link YearMonth}</li>
     *   <li>第二步：计算该月的时间范围 [月初, 下月初)，查询未结算（settleTime 为 null）的订单，
     *       最多取 {@value MAX_RECALC_ORDERS} 笔</li>
     *   <li>第三步：逐笔调用 {@link PerformanceCalculationService#upsertFromOrder} 重算业绩，
     *       单条失败不影响整批（catch-and-continue）</li>
     *   <li>第四步：汇总结果（扫描数、重算数、跳过数）并返回</li>
     * </ol>
     *
     * @param month  目标月份，格式 "yyyy-MM"
     * @param reason 重算原因（用于审计日志）
     * @return 重算结果响应，包含 jobId、状态和统计数字
     * @throws BusinessException month 或 reason 为空、格式不正确时抛出
     */
    public PerformanceRecalculateMonthResponse recalculateMonth(String month, String reason) {
        // 第一步：参数校验
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

        // 第二步：查询目标月份内未结算的订单
        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .ge(ColonelsettlementOrder::getCreateTime, start)
                .lt(ColonelsettlementOrder::getCreateTime, end)
                .isNull(ColonelsettlementOrder::getSettleTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime)
                .last("LIMIT " + MAX_RECALC_ORDERS));

        // 第三步：逐笔重算业绩
        int upserted = 0;
        int skippedSettled = 0;
        for (ColonelsettlementOrder order : orders) {
            // 注意：二次确认订单确实未结算
            if (order.getSettleTime() != null) {
                skippedSettled++;
                continue;
            }
            try {
                if (performanceCalculationService.upsertFromOrder(order) != null) {
                    upserted++;
                }
            } catch (Exception ignored) {
                // 注意：单条失败不影响整批重算
            }
        }

        // 第四步：构建返回结果
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
