package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class PerformanceTrackSummaryDTO {
    private long orderCount;
    private long orderAmount;
    private long serviceFeeIncome;
    private long techServiceFee;
    private long serviceFeeProfit;
    private long serviceFeeExpense;
    private long recruiterCommission;
    private long channelCommission;
    private long grossProfit;
}
