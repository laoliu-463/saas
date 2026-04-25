package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 商品库（Mock 阶段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    @TableField("product_id")
    private String productId;

    private String name;

    /**
     * 单位：分。
     */
    private Long price;

    /**
     * 1=上架, 0=下架。
     */
    private Integer status;

    private String category;

    @TableField("activity_id")
    private UUID activityId;

    @TableField("check_status")
    private Integer checkStatus;

    @TableField(exist = false)
    private String auditRemark;

    @TableField(exist = false)
    private UUID assigneeId;

    @TableField(exist = false)
    private String promoteLink;

    @TableField(exist = false)
    private String shortLink;

    @TableField(exist = false)
    private String bizStatus;

    @TableField(exist = false)
    private String bizStatusLabel;
}
