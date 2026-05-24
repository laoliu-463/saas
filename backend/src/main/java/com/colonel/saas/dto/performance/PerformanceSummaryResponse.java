package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class PerformanceSummaryResponse {
    private PerformanceTrackSummaryDTO estimate;
    private PerformanceTrackSummaryDTO effective;
}
