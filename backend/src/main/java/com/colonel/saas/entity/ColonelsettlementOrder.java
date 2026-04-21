package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "colonelsettlement_order", autoResultMap = true)
public class ColonelsettlementOrder implements Serializable {

    @TableId(type = IdType.INPUT)
    private UUID id;

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

    @TableField("order_amount")
    private Long orderAmount;

    @TableField("actual_amount")
    private Long actualAmount;

    @TableField("settle_colonel_commission")
    private Long settleColonelCommission;

    @TableField("settle_colonel_tech_service_fee")
    private Long settleColonelTechServiceFee;

    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("pick_source")
    private String pickSource;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private Integer deleted;

    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
