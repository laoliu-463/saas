package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 商品快照只读 DTO（DDD-PRODUCT-001）。
 */
public record ProductSnapshotReadDTO(
        UUID id,
        String activityId,
        String productId,
        String title,
        String cover,
        Long shopId,
        String shopName) {
}
