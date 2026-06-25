package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 商品摘要 DTO。
 */
public record ProductBriefDTO(
        UUID relationId,
        String productId,
        String title,
        String cover,
        Long price,
        String priceText,
        Long shopId,
        String shopName,
        Integer status,
        String statusText,
        String categoryName,
        String detailUrl
) {
}
