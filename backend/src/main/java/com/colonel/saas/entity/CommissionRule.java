package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commissions")
public class CommissionRule extends BaseEntity {

    @TableField("dimension_type")
    private String dimensionType;

    @TableField("dimension_id")
    private String dimensionId;

    @TableField("commission_type")
    private String commissionType;

    private BigDecimal ratio;

    @TableField("effective_start")
    private LocalDateTime effectiveStart;

    @TableField("effective_end")
    private LocalDateTime effectiveEnd;

    private Integer status;
}
