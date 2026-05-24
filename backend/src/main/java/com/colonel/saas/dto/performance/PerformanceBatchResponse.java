package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

@Data
public class PerformanceBatchResponse {
    private List<PerformanceBatchItemDTO> items;
}
