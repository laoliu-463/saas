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
@TableName("exclusive_merchant")
public class ExclusiveMerchant extends BaseEntity {

    @TableField("merchant_id")
    private String merchantId;

    @TableField("merchant_name")
    private String merchantName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("effective_month")
    private String effectiveMonth;

    @TableField("service_fee")
    private Long serviceFee;

    @TableField("business_total_fee")
    private Long businessTotalFee;

    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    private Integer status;

    private String remark;
}
