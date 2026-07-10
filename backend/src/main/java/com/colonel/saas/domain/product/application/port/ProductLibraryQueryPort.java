package com.colonel.saas.domain.product.application.port;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.entity.Product;

/**
 * 商品库查询端口。
 *
 * <p>应用层只依赖这个行为端口；Legacy 适配器可以在不改变 HTTP 契约的情况下
 * 逐步替换为商品域自己的查询编排。</p>
 */
public interface ProductLibraryQueryPort {

    IPage<Product> getSelectedLibraryPage(long page, long size, ProductLibraryPageQuery query);

    ProductLibraryCursorPage getSelectedLibraryCursorPage(
            String cursor,
            long limit,
            ProductLibraryPageQuery query);
}
