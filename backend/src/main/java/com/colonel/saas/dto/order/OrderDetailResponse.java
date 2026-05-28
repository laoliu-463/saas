package com.colonel.saas.dto.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单详情响应 DTO。
 * <p>
 * 返回订单完整详情页数据，包括订单基础状态、归属信息、来源标识，以及商品、渠道、
 * 达人、金额、推广、寄样、诊断和时间等多维度详情。
 * 关联业务领域：订单域（Order）。
 * </p>
 */
@Data
public class OrderDetailResponse {

    /** 订单 ID */
    private String orderId;
    /** 订单状态编码 */
    private Integer orderStatus;
    /** 订单状态中文描述 */
    private String orderStatusText;

    /** 归属状态编码 */
    private String attributionStatus;
    /** 归属状态中文描述 */
    private String attributionStatusText;
    /** 归属备注说明 */
    private String attributionRemark;

    /** 来源标识（pick_source） */
    private String pickSource;

    /** 商品信息 */
    private ProductInfo product;
    /** 渠道信息 */
    private ChannelInfo channel;
    /** 达人信息 */
    private TalentInfo talent;
    /** 金额信息 */
    private AmountInfo amount;
    /** 推广信息 */
    private PromotionInfo promotion;
    /** 寄样信息 */
    private SampleInfo sample;
    /** 诊断信息 */
    private DiagnosisInfo diagnosis;
    /** 时间信息 */
    private TimeInfo time;

    /**
     * 商品信息内部类。
     */
    @Data
    public static class ProductInfo {
        /** 商品 ID */
        private String productId;
        /** 商品名称 */
        private String productName;
        /** 活动 ID */
        private String activityId;
        /** 活动名称 */
        private String activityName;
        /** 团长用户 ID */
        private String colonelUserId;
        /** 团长姓名 */
        private String colonelName;
    }

    /**
     * 渠道信息内部类。
     */
    @Data
    public static class ChannelInfo {
        /** 渠道用户 ID */
        private String channelUserId;
        /** 渠道名称 */
        private String channelName;
    }

    /**
     * 达人信息内部类。
     */
    @Data
    public static class TalentInfo {
        /** 达人 ID */
        private String talentId;
        /** 达人 UID */
        private String talentUid;
        /** 作者 ID */
        private String authorId;
        /** 达人昵称 */
        private String talentName;
    }

    /**
     * 金额信息内部类（所有金额字段单位为分）。
     */
    @Data
    public static class AmountInfo {
        /** 订单金额（单位：分） */
        private Long orderAmount;
        /** 服务费（单位：分） */
        private Long serviceFee;
        /** 实付金额（单位：分） */
        private Long payAmount;
        /** 结算金额（单位：分） */
        private Long settleAmount;
        /** 预估服务费（单位：分） */
        private Long estimateServiceFee;
        /** 生效服务费（单位：分） */
        private Long effectiveServiceFee;
        /** 预估技术服务费（单位：分） */
        private Long estimateTechServiceFee;
        /** 生效技术服务费（单位：分） */
        private Long effectiveTechServiceFee;
    }

    /**
     * 推广信息内部类。
     */
    @Data
    public static class PromotionInfo {
        /** 是否已匹配推广关系 */
        private boolean matched;
        /** 来源标识（pick_source） */
        private String pickSource;
        /** 推广链接 */
        private String promotionUrl;
        /** 推广映射记录 ID */
        private String mappingId;
        /** 推广关系创建时间 */
        private LocalDateTime createdAt;
    }

    /**
     * 寄样信息内部类。
     */
    @Data
    public static class SampleInfo {
        /** 是否已匹配寄样记录 */
        private boolean matched;
        /** 寄样申请 ID */
        private String sampleRequestId;
        /** 寄样状态编码 */
        private String sampleStatus;
        /** 寄样状态中文描述 */
        private String sampleStatusText;
        /** 是否通过订单规则自动完成 */
        private boolean completedByOrderRule;
    }

    /**
     * 诊断信息内部类。
     */
    @Data
    public static class DiagnosisInfo {
        /** 诊断原因编码 */
        private String reasonCode;
        /** 诊断原因描述 */
        private String reasonText;
        /** 处理建议 */
        private String suggestion;
    }

    /**
     * 时间信息内部类。
     */
    @Data
    public static class TimeInfo {
        /** 订单创建时间 */
        private LocalDateTime createTime;
        /** 结算时间 */
        private LocalDateTime settleTime;
        /** 数据同步时间 */
        private LocalDateTime syncTime;
    }
}
