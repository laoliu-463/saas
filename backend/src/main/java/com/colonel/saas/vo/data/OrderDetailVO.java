package com.colonel.saas.vo.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细展示 VO。
 * <p>
 * 用于数据平台"订单明细"Tab 的逐单展示，聚合订单事实与业绩域提成数据，
 * 包含 16 列表头所需的全部字段。金额单位统一为元（BigDecimal），由后端从分转换。
 * </p>
 * <p>
 * 双轨金额：预估轨（estimate*）基于支付金额实时估算，结算轨（effective*）基于结算金额最终确认。
 * 未结算订单的 effective* 字段为 null，前端展示为 "-"。
 * </p>
 */
@Data
public class OrderDetailVO {

    // ── 订单基本信息 ──────────────────────────────

    /** 订单号（抖音侧订单号） */
    private String orderId;
    /** 订单状态码（1=已下单, 2=已发货, 3=已完成, 4=已取消） */
    private Integer orderStatus;
    /** 订单状态文本（待结算/已结算/已退款/已失效） */
    private String orderStatusText;
    /** 订单类型文本（如推广者推广/结算） */
    private String orderTypeText;

    // ── 活动信息 ──────────────────────────────

    /** 活动 ID */
    private String activityId;
    /** 活动名称 */
    private String activityName;
    /** 内容类型文本（短视频/直播） */
    private String contentTypeText;

    // ── 商品信息 ──────────────────────────────

    /** 商品 ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 商品图片 URL */
    private String productImage;
    /** 商品数量 */
    private Integer productQuantity;
    /** 佣金率（百分比，如 10 表示 10%） */
    private BigDecimal commissionRate;
    /** 服务费率（百分比，如 2 表示 2%） */
    private BigDecimal serviceFeeRate;

    // ── 合作方信息 ──────────────────────────────

    /** 合作方（商家）ID */
    private String partnerId;
    /** 合作方（商家）名称 */
    private String partnerName;
    /** 团长名称，来自订单事实中的 colonel_user_name */
    private String colonelName;

    // ── 推广者 ──────────────────────────────

    /** 达人 ID */
    private String talentId;
    /** 达人昵称 */
    private String talentName;
    /** 达人抖音 ID（如 tg12201212） */
    private String talentDouyinId;
    /** 出单视频 ID */
    private String videoId;

    // ── 渠道 ──────────────────────────────

    /** 渠道负责人 ID */
    private String channelId;
    /** 渠道负责人名称 */
    private String channelName;

    // ── 招商 ──────────────────────────────

    /** 招商负责人 ID */
    private String recruiterId;
    /** 招商负责人名称 */
    private String recruiterName;

    // ── 金额：订单额（元） ──────────────────────────────

    /** 支付金额（元） */
    private BigDecimal payAmount;
    /** 结算金额（元），未结算时为 null */
    private BigDecimal settleAmount;

    // ── 金额：服务费收入（元） ──────────────────────────────

    /** 预估服务费收入（元） */
    private BigDecimal estimateServiceFee;
    /** 生效服务费收入（元） */
    private BigDecimal effectiveServiceFee;

    // ── 金额：技术服务费（元） ──────────────────────────────

    /** 预估技术服务费（元） */
    private BigDecimal estimateTechServiceFee;
    /** 生效技术服务费（元） */
    private BigDecimal effectiveTechServiceFee;

    // ── 金额：服务费支出（元） ──────────────────────────────

    /** 预估服务费支出（元）= estimateServiceFee - estimateTechServiceFee - estimateServiceProfit */
    private BigDecimal estimateServiceFeeExpense;
    /** 生效服务费支出（元）= effectiveServiceFee - effectiveServiceProfit */
    private BigDecimal effectiveServiceFeeExpense;

    // ── 金额：服务费收益（元） ──────────────────────────────

    /** 预估服务费收益（元） */
    private BigDecimal estimateServiceProfit;
    /** 生效服务费收益（元） */
    private BigDecimal effectiveServiceProfit;

    // ── 金额：招商提成（元） ──────────────────────────────

    /** 预估招商提成（元） */
    private BigDecimal estimateRecruiterCommission;
    /** 生效招商提成（元） */
    private BigDecimal effectiveRecruiterCommission;

    // ── 金额：渠道提成（元） ──────────────────────────────

    /** 预估渠道提成（元） */
    private BigDecimal estimateChannelCommission;
    /** 生效渠道提成（元） */
    private BigDecimal effectiveChannelCommission;

    // ── 金额：毛利 = 服务费收益 - 招商提成 - 渠道提成（元） ──────────────────────────────

    /** 预估毛利（元）= estimateServiceProfit - estimateRecruiterCommission - estimateChannelCommission */
    private BigDecimal estimateGrossProfit;
    /** 生效毛利（元） */
    private BigDecimal effectiveGrossProfit;

    // ── 时间 ──────────────────────────────

    /** 付款时间 */
    private LocalDateTime payTime;
    /** 收货时间，上游返回时展示 */
    private LocalDateTime deliveryTime;
    /** 结算时间 */
    private LocalDateTime settleTime;
    /** 失效时间，上游返回时展示 */
    private LocalDateTime expireTime;
    /** 订单创建时间 */
    private LocalDateTime orderCreateTime;
    /** 结算状态文本（失效/已结算/待结算） */
    private String settleStatusText;
}
