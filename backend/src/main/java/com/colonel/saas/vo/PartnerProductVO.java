package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合作伙伴关联商品展示视图对象。
 * <p>
 * 用于合作伙伴详情页面中关联商品列表的展示，包含商品的基本信息、
 * 价格、销量、所属活动等数据。商品数据通过抖店 API 同步而来。
 * </p>
 *
 * @see com.colonel.saas.mapper.ProductMapper
 */
@Data
public class PartnerProductVO {
    /** 商品 ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 关联的活动 ID */
    private String activityId;
    /** 商品封面图 URL */
    private String cover;
    /** 价格展示文本（如 "99.90"） */
    private String priceText;
    /** 所属抖店店铺 ID */
    private Long shopId;
    /** 所属抖店店铺名称 */
    private String shopName;
    /** 商品类目名称 */
    private String categoryName;
    /** 商品销量 */
    private Long sales;
    /** 商品状态码 */
    private Integer status;
    /** 商品状态的中文展示文本 */
    private String statusText;
    /** 最近一次数据同步时间 */
    private LocalDateTime latestSyncTime;
}
