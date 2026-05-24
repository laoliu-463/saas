package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class PerformanceBatchItemDTO {
    private String orderId;
    private boolean found;
    private boolean authorized;
    private String message;
    private PerformanceDetailDTO performance;
}
