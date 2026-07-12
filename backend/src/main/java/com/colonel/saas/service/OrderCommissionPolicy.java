package com.colonel.saas.service;

/**
 * 订单业绩/提成口径策略工具类（对应需求 Y-06：取消/失效订单不计入业绩）。
 *
 * <p>提供统一的订单状态判断方法，决定某笔订单是否应计入提成和业绩统计。
 * 当前规则：失效（status=4）与已退款（status=5）不计入，其余状态均计入。</p>
 *
 * <ul>
 *   <li>提供 {@link #countsTowardCommission} 判断是否计入提成</li>
 *   <li>提供 {@link #countsTowardPerformance} 判断是否计入业绩</li>
 * </ul>
 *
 * <p><b>业务领域：</b>业绩域 — 提成/业绩计算口径</p>
 * <p><b>协作关系：</b>被 {@link PerformanceCalculationService}、{@link PerformanceSummaryService} 等业绩服务调用</p>
 *
 * @see PerformanceCalculationService
 * @see PerformanceSummaryService
 */
public final class OrderCommissionPolicy {

    /** 订单状态常量：已失效（对应抖店订单状态 4） */
    public static final int STATUS_CANCELLED = 4;

    /** 订单状态常量：已退款（对应抖店订单状态 5） */
    public static final int STATUS_REFUNDED = 5;

    private OrderCommissionPolicy() {
    }

    /**
     * 判断指定订单状态是否应计入提成。
     *
     * <ol>
     *   <li>若订单状态为 {@code null}，默认视为有效订单（计入提成）</li>
     *   <li>若订单状态为 {@link #STATUS_CANCELLED}（4）或 {@link #STATUS_REFUNDED}（5），则不计入提成</li>
     *   <li>其余状态均计入提成</li>
     * </ol>
     *
     * @param orderStatus 订单状态值，可能为 {@code null}
     * @return {@code true} 表示计入提成，{@code false} 表示不计入
     */
    public static boolean countsTowardCommission(Integer orderStatus) {
        // 第一步：null 状态视为有效订单
        if (orderStatus == null) {
            return true;
        }
        // 第二步：失效/退款订单不计入提成
        return orderStatus != STATUS_CANCELLED && orderStatus != STATUS_REFUNDED;
    }

    /**
     * 判断指定订单状态是否应计入业绩统计。
     * <p>当前逻辑与 {@link #countsTowardCommission} 完全一致（提成口径 = 业绩口径）。</p>
     *
     * @param orderStatus 订单状态值，可能为 {@code null}
     * @return {@code true} 表示计入业绩，{@code false} 表示不计入
     */
    public static boolean countsTowardPerformance(Integer orderStatus) {
        return countsTowardCommission(orderStatus);
    }

    public static boolean isInvalidatedStatus(Integer orderStatus) {
        return orderStatus != null && (orderStatus == STATUS_CANCELLED || orderStatus == STATUS_REFUNDED);
    }
}
