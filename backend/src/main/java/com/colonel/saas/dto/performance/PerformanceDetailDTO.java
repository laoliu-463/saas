package com.colonel.saas.dto.performance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PerformanceDetailDTO {
    private String orderId;
    private String productId;
    private String productName;
    private String activityId;
    private String activityName;
    private String partnerId;
    private String partnerName;
    private String talentId;
    private String talentName;

    private String defaultChannelId;
    private String defaultChannelName;
    private String defaultRecruiterId;
    private String defaultRecruiterName;

    private String finalChannelId;
    private String finalChannelName;
    private String finalRecruiterId;
    private String finalRecruiterName;

    private String channelAttributionType;
    private String recruiterAttributionType;

    private Long payAmount;
    private Long settleAmount;

    private Long estimateServiceFee;
    private Long effectiveServiceFee;
    private Long estimateTechServiceFee;
    private Long effectiveTechServiceFee;

    private Long estimateServiceProfit;
    private Long effectiveServiceProfit;

    private Long estimateRecruiterCommission;
    private Long effectiveRecruiterCommission;
    private Long estimateChannelCommission;
    private Long effectiveChannelCommission;

    private Long estimateGrossProfit;
    private Long effectiveGrossProfit;

    private BigDecimal recruiterCommissionRate;
    private BigDecimal channelCommissionRate;

    private String orderStatus;
    private LocalDateTime payTime;
    private LocalDateTime settleTime;
    private LocalDateTime calculatedAt;
    private Boolean valid;
    private Boolean reversed;
}
