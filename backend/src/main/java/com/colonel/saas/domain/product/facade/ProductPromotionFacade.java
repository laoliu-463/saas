package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.facade.dto.ProductPromotionCopyDTO;

import java.util.UUID;

/** 商品域面向寄样域提供的推广复制能力。 */
public interface ProductPromotionFacade {

    ProductPromotionCopyDTO copyForSample(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String talentId,
            String idempotencyKey);
}
