package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class PerformanceRecalculateMonthRequest {
    private String month;
    private String reason;
}
