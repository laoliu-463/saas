package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业绩列表项 DTO。
 * <p>
 * 用于业绩列表页展示的精简业绩记录，包含订单基础信息、归属渠道/招募人以及主要金额指标。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceListItemDTO {
    /** 订单 ID */
    private String orderId;
    /** 商品 ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 合作伙伴（商家）ID */
    private String partnerId;
    /** 合作伙伴名称 */
    private String partnerName;
    /** 达人 ID */
    private String talentId;
    /** 达人昵称 */
    private String talentName;
    /** 最终归属渠道 ID */
    private String finalChannelId;
    /** 最终归属渠道名称 */
    private String finalChannelName;
    /** 最终归属招募人 ID */
    private String finalRecruiterId;
    /** 最终归属招募人姓名 */
    private String finalRecruiterName;
    /** 支付金额（单位：分） */
    private Long payAmount;
    /** 结算金额（单位：分） */
    private Long settleAmount;
    /** 预估服务费利润（单位：分） */
    private Long estimateServiceProfit;
    /** 生效服务费利润（单位：分） */
    private Long effectiveServiceProfit;
    /** 预估招募人佣金（单位：分） */
    private Long estimateRecruiterCommission;
    /** 生效招募人佣金（单位：分） */
    private Long effectiveRecruiterCommission;
    /** 预估渠道佣金（单位：分） */
    private Long estimateChannelCommission;
    /** 生效渠道佣金（单位：分） */
    private Long effectiveChannelCommission;
    /** 预估毛利（单位：分） */
    private Long estimateGrossProfit;
    /** 生效毛利（单位：分） */
    private Long effectiveGrossProfit;
    /** 订单状态 */
    private String orderStatus;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 结算时间 */
    private LocalDateTime settleTime;
    /** 业绩计算时间 */
    private LocalDateTime calculatedAt;
}
