package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("performance_records")
public class PerformanceRecord {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("order_id")
    private String orderId;

    @TableField("order_row_id")
    private UUID orderRowId;

    @TableField("default_channel_user_id")
    private UUID defaultChannelUserId;

    @TableField("default_recruiter_user_id")
    private UUID defaultRecruiterUserId;

    @TableField("final_channel_user_id")
    private UUID finalChannelUserId;

    @TableField("final_recruiter_user_id")
    private UUID finalRecruiterUserId;

    @TableField("channel_attribution")
    private String channelAttribution;

    @TableField("recruiter_attribution")
    private String recruiterAttribution;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("pay_amount")
    private Long payAmount;

    @TableField("settle_amount")
    private Long settleAmount;

    @TableField("estimate_service_fee")
    private Long estimateServiceFee;

    @TableField("effective_service_fee")
    private Long effectiveServiceFee;

    @TableField("estimate_tech_service_fee")
    private Long estimateTechServiceFee;

    @TableField("effective_tech_service_fee")
    private Long effectiveTechServiceFee;

    @TableField("estimate_service_profit")
    private Long estimateServiceProfit;

    @TableField("effective_service_profit")
    private Long effectiveServiceProfit;

    @TableField("estimate_recruiter_commission")
    private Long estimateRecruiterCommission;

    @TableField("effective_recruiter_commission")
    private Long effectiveRecruiterCommission;

    @TableField("estimate_channel_commission")
    private Long estimateChannelCommission;

    @TableField("effective_channel_commission")
    private Long effectiveChannelCommission;

    @TableField("estimate_gross_profit")
    private Long estimateGrossProfit;

    @TableField("effective_gross_profit")
    private Long effectiveGrossProfit;

    @TableField("recruiter_commission_rate")
    private BigDecimal recruiterCommissionRate;

    @TableField("channel_commission_rate")
    private BigDecimal channelCommissionRate;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    @TableField("order_create_time")
    private LocalDateTime orderCreateTime;

    @TableField("is_valid")
    private Boolean valid;

    @TableField("is_reversed")
    private Boolean reversed;

    @TableField("calculation_version")
    private Integer calculationVersion;

    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
