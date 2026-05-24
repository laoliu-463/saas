package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_snapshot")
public class ProductSnapshot extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    @TableField("product_id")
    private String productId;

    private String title;
    private String cover;
    private Long price;

    @TableField("price_text")
    private String priceText;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    private Integer status;

    @TableField("status_text")
    private String statusText;

    @TableField("category_name")
    private String categoryName;

    @TableField("product_stock")
    private String productStock;

    private Long sales;

    @TableField("detail_url")
    private String detailUrl;

    @TableField("promotion_start_time")
    private String promotionStartTime;

    @TableField("promotion_end_time")
    private String promotionEndTime;

    @TableField("activity_cos_ratio")
    private Long activityCosRatio;

    @TableField("activity_cos_ratio_text")
    private String activityCosRatioText;

    @TableField("cos_type")
    private Integer cosType;

    @TableField("cos_type_text")
    private String cosTypeText;

    @TableField("ad_service_ratio")
    private String adServiceRatio;

    @TableField("activity_ad_cos_ratio")
    private Long activityAdCosRatio;

    @TableField("has_douin_goods_tag")
    private Boolean hasDouinGoodsTag;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("sync_time")
    private LocalDateTime syncTime;

    /**
     * 保护期月数，来自 colonel_activity.months_of_protection，非数据库持久化字段。
     */
    @TableField(exist = false)
    private Integer monthsOfProtection;
}

