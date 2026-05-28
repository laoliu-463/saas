package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

/**
 * 业绩批量查询请求 DTO。
 * <p>
 * 用于根据订单 ID 列表批量查询订单的业绩详情。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceBatchRequest {
    /** 待查询的订单 ID 列表 */
    private List<String> orderIds;
}
