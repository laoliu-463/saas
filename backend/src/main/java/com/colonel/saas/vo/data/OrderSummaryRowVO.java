package com.colonel.saas.vo.data;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单汇总表行 VO。
 * <p>
 * 对应订单汇总报表中的每一行数据，按日期聚合各维度指标，
 * 包括推广人数、商品数、订单量、金额及各项费率与利润拆解。
 * </p>
 */
@Data
public class OrderSummaryRowVO {
    /** 汇总日期，格式通常为 {@code yyyy-MM-dd} */
    private String date;
    /** 达人推广者数量（当日参与带货的达人数） */
    private Long talentPromoterCount;
    /** 团长推广者数量（当日参与推广的团长数） */
    private Long colonelPromoterCount;
    /** 参与商品数量（当日有订单的商品 SKU 数） */
    private Long productCount;
    /** 订单数量 */
    private Long orderCount;
    /** 订单总金额，单位：元 */
    private BigDecimal orderAmount;
    /** 退款订单数量 */
    private Long refundOrderCount;
    /** 退款订单额，单位：元 */
    private BigDecimal refundOrderAmount;
    /** 订单退款服务费，单位：元 */
    private BigDecimal refundServiceFee;
    /** 商品维度平均服务费率（按商品加权平均） */
    private BigDecimal productAverageServiceFeeRate;
    /** 订单维度平均服务费率（按订单加权平均） */
    private BigDecimal orderAverageServiceFeeRate;
    /** 服务费收入（平台收取），单位：元 */
    private BigDecimal serviceFeeIncome;
    /** 技术服务费，单位：元 */
    private BigDecimal techServiceFee;
    /** 服务费支出（支付给达人/合作方），单位：元 */
    private BigDecimal serviceFeeExpense;
    /** 服务费利润（收入 - 支出），单位：元 */
    private BigDecimal serviceFeeProfit;
    /** 毛利润，单位：元 */
    private BigDecimal grossProfit;
}
