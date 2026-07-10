package com.colonel.saas.domain.product.application.dto;

import com.colonel.saas.entity.Product;

import java.util.List;

/**
 * 商品库游标查询结果。
 *
 * <p>这是应用层返回契约，避免 Controller 继续依赖 ProductService 的内部 record。</p>
 */
public record ProductLibraryCursorPage(
        List<Product> records,
        long limit,
        boolean hasMore,
        String nextCursor) {

    public static ProductLibraryCursorPage empty(long limit) {
        return new ProductLibraryCursorPage(List.of(), limit, false, null);
    }
}
