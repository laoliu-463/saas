package com.colonel.saas.dto.performance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业绩详情 DTO。
 * <p>
 * 表示单条订单的完整业绩归属和金额拆分详情，包括商品信息、合作伙伴、达人、渠道、
 * 招募人归属，以及预估/生效的服务费、技术费、渠道佣金、招募人佣金、毛利等财务数据。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceDetailDTO {
    /** 订单 ID */
    private String orderId;
    /** 商品 ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 活动 ID */
    private String activityId;
    /** 活动名称 */
    private String activityName;
    /** 合作伙伴（商家）ID */
    private String partnerId;
    /** 合作伙伴名称 */
    private String partnerName;
    /** 达人 ID */
    private String talentId;
    /** 达人昵称 */
    private String talentName;

    /** 默认渠道 ID（原始归属） */
    private String defaultChannelId;
    /** 默认渠道名称 */
    private String defaultChannelName;
    /** 默认招募人 ID（原始归属） */
    private String defaultRecruiterId;
    /** 默认招募人姓名 */
    private String defaultRecruiterName;

    /** 最终归属渠道 ID（含冲正/覆盖后） */
    private String finalChannelId;
    /** 最终归属渠道名称 */
    private String finalChannelName;
    /** 最终归属招募人 ID（含冲正/覆盖后） */
    private String finalRecruiterId;
    /** 最终归属招募人姓名 */
    private String finalRecruiterName;

    /** 渠道归属类型（如 default、override） */
    private String channelAttributionType;
    /** 招募人归属类型（如 default、override） */
    private String recruiterAttributionType;

    /** 支付金额（单位：分） */
    private Long payAmount;
    /** 结算金额（单位：分） */
    private Long settleAmount;

    /** 预估服务费收入（单位：分） */
    private Long estimateServiceFee;
    /** 生效服务费收入（单位：分） */
    private Long effectiveServiceFee;
    /** 预估技术服务费（单位：分） */
    private Long estimateTechServiceFee;
    /** 生效技术服务费（单位：分） */
    private Long effectiveTechServiceFee;

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

    /** 招募人佣金比例 */
    private BigDecimal recruiterCommissionRate;
    /** 渠道佣金比例 */
    private BigDecimal channelCommissionRate;

    /** 订单状态 */
    private String orderStatus;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 结算时间 */
    private LocalDateTime settleTime;
    /** 业绩计算时间 */
    private LocalDateTime calculatedAt;
    /** 该条业绩记录是否有效 */
    private Boolean valid;
    /** 该条业绩记录是否已被冲正 */
    private Boolean reversed;
}
