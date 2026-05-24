package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class PerformanceRecalculateMonthResponse {
    private String jobId;
    private String status;
    private String month;
    private int scanned;
    private int upserted;
    private int skippedSettled;
}
