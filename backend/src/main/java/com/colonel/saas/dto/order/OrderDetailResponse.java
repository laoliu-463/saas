package com.colonel.saas.dto.order;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderDetailResponse {

    private String orderId;
    private Integer orderStatus;
    private String orderStatusText;

    private String attributionStatus;
    private String attributionStatusText;
    private String attributionRemark;

    private String pickSource;

    private ProductInfo product;
    private ChannelInfo channel;
    private TalentInfo talent;
    private AmountInfo amount;
    private PromotionInfo promotion;
    private SampleInfo sample;
    private DiagnosisInfo diagnosis;
    private TimeInfo time;

    @Data
    public static class ProductInfo {
        private String productId;
        private String productName;
        private String activityId;
        private String activityName;
        private String colonelUserId;
        private String colonelName;
    }

    @Data
    public static class ChannelInfo {
        private String channelUserId;
        private String channelName;
    }

    @Data
    public static class TalentInfo {
        private String talentId;
        private String talentUid;
        private String authorId;
        private String talentName;
    }

    @Data
    public static class AmountInfo {
        private Long orderAmount;
        private Long serviceFee;
        private Long payAmount;
        private Long settleAmount;
        private Long estimateServiceFee;
        private Long effectiveServiceFee;
        private Long estimateTechServiceFee;
        private Long effectiveTechServiceFee;
    }

    @Data
    public static class PromotionInfo {
        private boolean matched;
        private String pickSource;
        private String promotionUrl;
        private String mappingId;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SampleInfo {
        private boolean matched;
        private String sampleRequestId;
        private String sampleStatus;
        private String sampleStatusText;
        private boolean completedByOrderRule;
    }

    @Data
    public static class DiagnosisInfo {
        private String reasonCode;
        private String reasonText;
        private String suggestion;
    }

    @Data
    public static class TimeInfo {
        private LocalDateTime createTime;
        private LocalDateTime settleTime;
        private LocalDateTime syncTime;
    }
}
