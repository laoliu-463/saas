package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

@Data
public class PerformanceBatchRequest {
    private List<String> orderIds;
}
