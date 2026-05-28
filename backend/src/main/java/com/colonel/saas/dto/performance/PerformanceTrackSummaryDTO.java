package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 业绩轨道汇总 DTO。
 * <p>
 * 表示单一轨道（预估或生效）下的业绩汇总数据，包含订单数、金额、各类收入/支出和利润指标。
 * 所有金额字段单位为分。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceTrackSummaryDTO {
    /** 订单数 */
    private long orderCount;
    /** 订单总金额（单位：分） */
    private long orderAmount;
    /** 服务费收入（单位：分） */
    private long serviceFeeIncome;
    /** 技术服务费（单位：分） */
    private long techServiceFee;
    /** 服务费利润（单位：分） */
    private long serviceFeeProfit;
    /** 服务费支出（单位：分） */
    private long serviceFeeExpense;
    /** 招募人佣金（单位：分） */
    private long recruiterCommission;
    /** 渠道佣金（单位：分） */
    private long channelCommission;
    /** 毛利（单位：分） */
    private long grossProfit;
}
