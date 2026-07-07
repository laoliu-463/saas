package com.colonel.saas.domain.shared.application.dto;

/**
 * 抖音商品 SKU 联调视图。
 */
public record DouyinProductSkuView(
        String skuId,
        String skuName,
        Long price,
        Integer stock,
        String cover
) {
}
