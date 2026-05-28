package com.colonel.saas.dto.display;

import java.util.UUID;

/**
 * 强制展示操作响应 DTO。
 * <p>
 * 返回强制展示/取消展示操作的执行结果，包含操作状态、商品 ID 和展示关系 ID。
 * 关联业务领域：展示域（Display）。
 * </p>
 */
public record ForceDisplayResponse(
        /** 操作是否成功 */
        boolean success,
        /** 操作涉及的商品 ID */
        String productId,
        /** 展示关系 ID */
        UUID displayRelationId) {
}
