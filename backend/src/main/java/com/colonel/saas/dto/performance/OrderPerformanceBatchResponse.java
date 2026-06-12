package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

/**
 * 订单列表/详情 BFF 批量业绩响应（DDD-PERF-004）。
 */
@Data
public class OrderPerformanceBatchResponse {
    private List<OrderPerformanceDTO> items;
}