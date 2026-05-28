package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

/**
 * 业绩批量查询响应 DTO。
 * <p>
 * 返回批量查询订单业绩的结果列表，每条包含查询状态和业绩详情。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceBatchResponse {
    /** 每条订单的查询结果列表 */
    private List<PerformanceBatchItemDTO> items;
}
