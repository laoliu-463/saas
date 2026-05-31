package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "colonel_order_settlement", autoResultMap = true)
public class ColonelOrderSettlement implements Serializable {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("upstream_key")
    private String upstreamKey;

    @TableField("order_id")
    private String orderId;

    @TableField("product_id")
    private String productId;

    @TableField("product_name")
    private String productName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

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

    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    @TableField("source_amount_unit")
    private String sourceAmountUnit;

    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    @TableField("colonel_name")
    private String colonelName;

    @TableField("service_fee_rate")
    private BigDecimal serviceFeeRate;

    @TableField("commission_rate")
    private BigDecimal commissionRate;

    @TableField("colonel_activity_id")
    private String colonelActivityId;

    @TableField("second_colonel_buyin_id")
    private Long secondColonelBuyinId;

    @TableField("second_colonel_activity_id")
    private String secondColonelActivityId;

    @TableField("phase_id")
    private String phaseId;

    @TableField("pick_source")
    private String pickSource;

    @TableField("talent_external_id")
    private String talentExternalId;

    @TableField("talent_name")
    private String talentName;

    @TableField("flow_point")
    private String flowPoint;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("order_create_time")
    private LocalDateTime orderCreateTime;

    @TableField("pay_time")
    private LocalDateTime payTime;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    @TableField("delivery_time")
    private LocalDateTime deliveryTime;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("cursor")
    private String cursor;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("deleted")
    private Integer deleted;

    @TableField(value = "raw_payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawPayload;
}
