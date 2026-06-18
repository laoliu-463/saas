package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 商品负责人 DTO。
 */
public record ProductOwnerDTO(
        UUID relationId,
        String activityId,
        String productId,
        UUID ownerUserId,
        UUID ownerDeptId,
        String ownerSource
) {
}
