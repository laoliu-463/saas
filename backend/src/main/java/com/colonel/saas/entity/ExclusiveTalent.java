package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exclusive_talent")
public class ExclusiveTalent extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("exclusive_type")
    private Integer exclusiveType;

    @TableField("effective_month")
    private String effectiveMonth;

    @TableField("service_fee")
    private Long serviceFee;

    @TableField("channel_total_fee")
    private Long channelTotalFee;

    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    @TableField("monthly_samples")
    private Integer monthlySamples;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    private Integer status;

    private String remark;

    @TableField("trigger_type")
    private Integer triggerType;
}
