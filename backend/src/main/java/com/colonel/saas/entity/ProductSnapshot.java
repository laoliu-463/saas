package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品快照实体。
 * <p>
 * 对应数据库表：{@code product_snapshot}，记录商品在特定时间点的完整数据快照。
 * 当商品与活动建立关联时，系统自动抓取商品的当前状态并存储为快照，
 * 用于后续比对商品信息变更、保护期判定等场景。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Product 商品实体
 * @see ProductOperationState 商品运营状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_snapshot")
public class ProductSnapshot extends BaseEntity {

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，商品关联的抖店活动 ID</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，抖店平台的商品 ID</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 商品标题
     * <p>对应数据库列：{@code title}，商品的展示标题</p>
     */
    private String title;

    /**
     * 商品封面图
     * <p>对应数据库列：{@code cover}，商品主图的 CDN URL</p>
     */
    private String cover;

    /**
     * 商品价格
     * <p>对应数据库列：{@code price}，商品价格（单位：分）</p>
     */
    private Long price;

    /**
     * 价格文本
     * <p>对应数据库列：{@code price_text}，商品价格的展示文本，如"99.00"</p>
     */
    @TableField("price_text")
    private String priceText;

    /**
     * 店铺 ID
     * <p>对应数据库列：{@code shop_id}，商品所属店铺的抖店 ID</p>
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 店铺名称
     * <p>对应数据库列：{@code shop_name}，商品所属店铺的名称</p>
     */
    @TableField("shop_name")
    private String shopName;

    /**
     * 商品状态
     * <p>对应数据库列：{@code status}，商品在抖店的状态编码</p>
     */
    private Integer status;

    /**
     * 状态文本
     * <p>对应数据库列：{@code status_text}，商品状态的中文显示名称</p>
     */
    @TableField("status_text")
    private String statusText;

    /**
     * 类目名称
     * <p>对应数据库列：{@code category_name}，商品所属类目的名称</p>
     */
    @TableField("category_name")
    private String categoryName;

    /**
     * 库存
     * <p>对应数据库列：{@code product_stock}，商品的当前库存数量</p>
     */
    @TableField("product_stock")
    private String productStock;

    /**
     * 销量
     * <p>对应数据库列：{@code sales}，商品的累计销量</p>
     */
    private Long sales;

    /**
     * 详情页链接
     * <p>对应数据库列：{@code detail_url}，商品详情页的完整 URL</p>
     */
    @TableField("detail_url")
    private String detailUrl;

    /**
     * 活动推广开始时间
     * <p>对应数据库列：{@code promotion_start_time}，商品参与活动的推广开始时间</p>
     */
    @TableField("promotion_start_time")
    private String promotionStartTime;

    /**
     * 活动推广结束时间
     * <p>对应数据库列：{@code promotion_end_time}，商品参与活动的推广结束时间</p>
     */
    @TableField("promotion_end_time")
    private String promotionEndTime;

    /**
     * 活动佣金比例
     * <p>对应数据库列：{@code activity_cos_ratio}，活动设置的佣金比例（单位：分/万）</p>
     */
    @TableField("activity_cos_ratio")
    private Long activityCosRatio;

    /**
     * 活动佣金比例文本
     * <p>对应数据库列：{@code activity_cos_ratio_text}，佣金比例的展示文本，如"20%"</p>
     */
    @TableField("activity_cos_ratio_text")
    private String activityCosRatioText;

    /**
     * 佣金类型
     * <p>对应数据库列：{@code cos_type}，佣金的计算类型编码</p>
     */
    @TableField("cos_type")
    private Integer cosType;

    /**
     * 佣金类型文本
     * <p>对应数据库列：{@code cos_type_text}，佣金类型的中文显示名称</p>
     */
    @TableField("cos_type_text")
    private String cosTypeText;

    /**
     * 广告服务费比例
     * <p>对应数据库列：{@code ad_service_ratio}，广告服务费比例</p>
     */
    @TableField("ad_service_ratio")
    private String adServiceRatio;

    /**
     * 活动广告佣金比例
     * <p>对应数据库列：{@code activity_ad_cos_ratio}，活动设置的广告佣金比例</p>
     */
    @TableField("activity_ad_cos_ratio")
    private Long activityAdCosRatio;

    /**
     * 是否有抖音商品标签
     * <p>对应数据库列：{@code has_douin_goods_tag}，标记商品是否带有抖音商品标签</p>
     */
    @TableField("has_douin_goods_tag")
    private Boolean hasDouinGoodsTag;

    /**
     * 原始响应载荷
     * <p>对应数据库列：{@code raw_payload}，数据源返回的完整原始数据（JSON 字符串）</p>
     */
    @TableField("raw_payload")
    private String rawPayload;

    /**
     * 同步时间
     * <p>对应数据库列：{@code sync_time}，数据源同步该快照的时间</p>
     */
    @TableField("sync_time")
    private LocalDateTime syncTime;

    /**
     * 保护期月数（非持久化）
     * <p>非数据库持久化字段（exist = false），来自 colonel_activity.months_of_protection，
     * 用于保护期判定逻辑</p>
     */
    @TableField(exist = false)
    private Integer monthsOfProtection;
}

