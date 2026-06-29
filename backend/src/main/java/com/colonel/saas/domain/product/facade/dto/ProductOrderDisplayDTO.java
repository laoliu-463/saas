package com.colonel.saas.domain.product.facade.dto;

import java.math.BigDecimal;

/**
 * 订单列表展示所需的商品主数据只读模型。
 */
public record ProductOrderDisplayDTO(
        String productId,
        String outerProductId,
        String name,
        String cover,
        BigDecimal cosRatio,
        BigDecimal serviceRatio) {
}
