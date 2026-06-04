package com.colonel.saas.vo.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表展示 VO。
 * <p>
 * 用于 Dashboard 订单列表、订单详情等场景的订单数据展示，
 * 包含订单基本信息、金额构成、状态及时间线。
 * </p>
 */
@Data
public class OrderVO {
    /** 订单唯一标识（抖音侧订单号或系统内部 ID） */
    private String id;
    /** 商品 ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 商品图片 URL */
    private String productImage;
    /** 店铺名称 */
    private String shopName;
    /** 商品数量 */
    private Integer productQuantity;
    /** 佣金率，原始单位沿用上游/订单扩展字段 */
    private BigDecimal commissionRate;
    /** 服务费率，原始单位沿用上游/订单扩展字段 */
    private BigDecimal serviceFeeRate;
    /** 渠道负责人 ID */
    private String channelId;
    /** 渠道负责人名称 */
    private String channelName;
    /** 达人名称（带货达人昵称） */
    private String talentName;
    /** 订单总金额（含佣金、运费等），单位：元 */
    private BigDecimal amount;
    /** 商品售价（不含运费），单位：元 */
    private BigDecimal goodsPrice;
    /** 佣金金额，单位：元 */
    private BigDecimal commission;
    /** 运费，单位：元 */
    private BigDecimal freight;
    /** 订单状态（如：待发货、已发货、已签收、已完成、已关闭等） */
    private String status;
    /** 归属来源标识（用于区分订单归因渠道，如 talent / colonel 等） */
    private String attributionSource;
    /** 订单创建时间 */
    private LocalDateTime createTime;
    /** 订单结算时间（结算完成后填充） */
    private LocalDateTime settleTime;
}
