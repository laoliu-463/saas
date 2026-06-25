package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 订单列表/详情 BFF 使用的业绩补全 DTO（DDD-PERF-004）。
 */
@Data
public class OrderPerformanceDTO {
    private String orderId;
    private String finalChannelId;
    private String finalChannelName;
    private String finalRecruiterId;
    private String finalRecruiterName;
    private String channelAttributionType;
    private String recruiterAttributionType;
    private Long estimateServiceProfit;
    private Long effectiveServiceProfit;
    private Long estimateServiceFeeExpense;
    private Long effectiveServiceFeeExpense;
    private Long estimateRecruiterCommission;
    private Long effectiveRecruiterCommission;
    private Long estimateChannelCommission;
    private Long effectiveChannelCommission;
    private Long estimateGrossProfit;
    private Long effectiveGrossProfit;
    private Boolean isValid;
    private Boolean isReversed;
}
