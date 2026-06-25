package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 商品域只读 DTO（DDD-PRODUCT-001）：跨域查询商品主数据时的字段集。
 */
public record ProductReadDTO(
        UUID id,
        String productId,
        String outerProductId,
        String name,
        String cover,
        Long price,
        UUID activityId,
        String detailUrl,
        Integer status,
        Integer checkStatus) {
}
