package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 团长活动（Test 阶段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "colonel_activity", autoResultMap = true)
public class ColonelsettlementActivity extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("activity_name")
    private String name;

    @TableField("activity_type")
    private String activityType;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    @TableField("commission_rate")
    private BigDecimal commissionRate;

    @TableField("service_rate")
    private BigDecimal serviceRate;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    @TableField("months_of_protection")
    private Integer monthsOfProtection;

    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    /**
     * 1=进行中, 0=已结束。
     */
    @TableField(exist = false)
    private Integer status;
}

