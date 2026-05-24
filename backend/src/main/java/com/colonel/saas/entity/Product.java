package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品库（Test 阶段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product", autoResultMap = true)
public class Product extends BaseEntity {

    @TableField("product_id")
    private String productId;

    @TableField("outer_product_id")
    private String outerProductId;

    private String name;

    private String description;

    @TableField("market_price")
    private Long marketPrice;

    /**
     * 单位：分。
     */
    @TableField("discount_price")
    private Long price;

    private String cover;

    @TableField("detail_url")
    private String detailUrl;

    @TableField("first_cid")
    private Long firstCid;

    @TableField("second_cid")
    private Long secondCid;

    @TableField("third_cid")
    private Long thirdCid;

    @TableField("fourth_cid")
    private Long fourthCid;

    @TableField(value = "category_detail", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> categoryDetail;

    @TableField(value = "pics", typeHandler = JacksonTypeHandler.class)
    private List<String> pics;

    @TableField(value = "spec_prices", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> specPrices;

    @TableField("cos_ratio")
    private BigDecimal cosRatio;

    @TableField("cos_fee")
    private Long cosFee;

    @TableField("service_ratio")
    private BigDecimal serviceRatio;

    /**
     * 1=上架, 0=下架。
     */
    private Integer status;

    @TableField(exist = false)
    private String category;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String statusText;

    @TableField(exist = false)
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

    @TableField(exist = false)
    private String shopName;

    @TableField(exist = false)
    private String priceText;

    @TableField(exist = false)
    private String activityCosRatioText;

    @TableField(exist = false)
    private String estimatedServiceFee;

    @TableField(exist = false)
    private String assigneeName;

    @TableField(exist = false)
    private String sourceActivityId;

    @TableField(exist = false)
    private Boolean selectedToLibrary;

    @TableField(exist = false)
    private List<String> systemTags;

    @TableField(exist = false)
    private List<String> alertTags;

    @TableField(exist = false)
    private Map<String, Object> auditSupplement;

    @TableField(exist = false)
    private java.time.LocalDateTime selectedAt;

    @TableField(exist = false)
    private String latestDecisionLevel;

    @TableField(exist = false)
    private String latestDecisionLabel;

    @TableField(exist = false)
    private String latestDecisionReason;

    @TableField(exist = false)
    private String latestDecisionAt;

    @TableField(exist = false)
    private Boolean hasMaterial;

    @TableField(exist = false)
    private Boolean hasSampleRule;

    @TableField(exist = false)
    private Long sales30d;

    @TableField(exist = false)
    private Boolean pinned;

    @TableField(exist = false)
    private LocalDateTime pinnedUntil;

    @TableField(exist = false)
    private String displayStatus;

    @TableField(exist = false)
    private String displayStatusLabel;

    @TableField(exist = false)
    private String hiddenReason;

    @TableField(exist = false)
    private Boolean supportsAds;

    @TableField(exist = false)
    private String adsRule;
}
