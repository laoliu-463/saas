package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 退款、冲正等对业绩结果产生影响的独立调整流水。 */
@Data
@TableName(value = "performance_adjustment_ledger", autoResultMap = true)
public class PerformanceAdjustmentLedger {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("event_key")
    private String eventKey;

    @TableField("order_id")
    private String orderId;

    @TableField("refund_id")
    private String refundId;

    /** REFUND / REVERSAL。 */
    @TableField("adjustment_type")
    private String adjustmentType;

    @TableField("refund_amount")
    private Long refundAmount;

    @TableField("delta_pay_amount")
    private Long deltaPayAmount;

    @TableField("delta_settle_amount")
    private Long deltaSettleAmount;

    @TableField("delta_estimate_service_fee")
    private Long deltaEstimateServiceFee;

    @TableField("delta_effective_service_fee")
    private Long deltaEffectiveServiceFee;

    @TableField("delta_estimate_tech_service_fee")
    private Long deltaEstimateTechServiceFee;

    @TableField("delta_effective_tech_service_fee")
    private Long deltaEffectiveTechServiceFee;

    @TableField("delta_estimate_service_fee_expense")
    private Long deltaEstimateServiceFeeExpense;

    @TableField("delta_effective_service_fee_expense")
    private Long deltaEffectiveServiceFeeExpense;

    @TableField("delta_talent_commission")
    private Long deltaTalentCommission;

    @TableField("delta_estimate_service_profit")
    private Long deltaEstimateServiceProfit;

    @TableField("delta_effective_service_profit")
    private Long deltaEffectiveServiceProfit;

    @TableField("delta_estimate_recruiter_commission")
    private Long deltaEstimateRecruiterCommission;

    @TableField("delta_effective_recruiter_commission")
    private Long deltaEffectiveRecruiterCommission;

    @TableField("delta_estimate_channel_commission")
    private Long deltaEstimateChannelCommission;

    @TableField("delta_effective_channel_commission")
    private Long deltaEffectiveChannelCommission;

    @TableField("delta_estimate_gross_profit")
    private Long deltaEstimateGrossProfit;

    @TableField("delta_effective_gross_profit")
    private Long deltaEffectiveGrossProfit;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField(value = "input_snapshot", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSnapshot;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
