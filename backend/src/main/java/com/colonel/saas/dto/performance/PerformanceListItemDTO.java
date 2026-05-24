package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PerformanceListItemDTO {
    private String orderId;
    private String productId;
    private String productName;
    private String partnerId;
    private String partnerName;
    private String talentId;
    private String talentName;
    private String finalChannelId;
    private String finalChannelName;
    private String finalRecruiterId;
    private String finalRecruiterName;
    private Long payAmount;
    private Long settleAmount;
    private Long estimateServiceProfit;
    private Long effectiveServiceProfit;
    private Long estimateRecruiterCommission;
    private Long effectiveRecruiterCommission;
    private Long estimateChannelCommission;
    private Long effectiveChannelCommission;
    private Long estimateGrossProfit;
    private Long effectiveGrossProfit;
    private String orderStatus;
    private LocalDateTime payTime;
    private LocalDateTime settleTime;
    private LocalDateTime calculatedAt;
}
