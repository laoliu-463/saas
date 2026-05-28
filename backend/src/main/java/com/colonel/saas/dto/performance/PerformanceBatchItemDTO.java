package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 业绩批量查询单项结果 DTO。
 * <p>
 * 表示批量查询订单业绩时单条订单的查询结果，包含查询状态和对应的业绩详情。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceBatchItemDTO {
    /** 订单 ID */
    private String orderId;
    /** 是否找到对应业绩记录 */
    private boolean found;
    /** 当前用户是否有权查看该条记录 */
    private boolean authorized;
    /** 查询结果描述信息 */
    private String message;
    /** 业绩详情（found=true 且 authorized=true 时有值） */
    private PerformanceDetailDTO performance;
}
