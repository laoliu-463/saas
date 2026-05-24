package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

@Data
public class PerformancePageResponse {
    private long page;
    private long pageSize;
    private long total;
    private List<PerformanceListItemDTO> items;
}
