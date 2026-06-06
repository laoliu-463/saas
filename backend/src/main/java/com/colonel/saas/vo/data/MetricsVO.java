package com.colonel.saas.vo.data;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 核心指标汇总 VO。
 * <p>
 * 用于 Dashboard 首页的全局指标卡片与趋势图数据展示，
 * 涵盖今日实时数据、累计数据、费用拆解及毛利计算。
 * 可配合 {@link DualTrackMetricsVO} 同时输出已结算和预估两套口径。
 * </p>
 */
@Data
public class MetricsVO {
    /** 今日订单数量 */
    private Long todayOrderCount;
    /** 今日 GMV（商品交易总额），单位：元 */
    private BigDecimal todayGmv;
    /** 待发货订单数量 */
    private Long pendingShipCount;
    /** 近 7 日趋势数据（按日聚合的订单量与 GMV） */
    private List<TrendPointVO> trend7d;
    /** 累计订单总数 */
    private Long totalOrders;
    /** 累计订单总金额，单位：元 */
    private BigDecimal totalAmount;
    /** 服务费总额，单位：元 */
    private BigDecimal serviceFee;
    /** 佣金总额，单位：元 */
    private BigDecimal commission;
    /** 服务费收入（平台侧收取），单位：元 */
    private BigDecimal serviceFeeIncome;
    /** 技术服务费，单位：元 */
    private BigDecimal techServiceFee;
    /** 服务费支出（平台侧实际服务费），单位：元 */
    private BigDecimal serviceFeeExpense;
    /** 达人佣金，单位：元 */
    private BigDecimal talentCommission;
    /** 业务佣金，单位：元 */
    private BigDecimal bizCommission;
    /** 渠道佣金，单位：元 */
    private BigDecimal channelCommission;
    /** 毛利润（收入 - 成本），单位：元 */
    private BigDecimal grossProfit;
    /** 金额口径标识（如 settle / estimate） */
    private String amountTrack;
    /** 指标数据来源说明（如数据库聚合、缓存、实时计算等） */
    private String metricsSource;
    /** 轨道标识（与 amountTrack 对应，用于前端条件渲染） */
    private String track;
}
