package com.colonel.saas.domain.product.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.entity.Product;
import org.springframework.stereotype.Service;

/**
 * Product library page query facade.
 *
 * <p>只依赖 ProductLibraryApplicationService；Legacy ProductService 依赖收口在
 * application port 的适配器中。</p>
 */
@Service
public class ProductLibraryPageQueryService {

    private final ProductLibraryApplicationService applicationService;

    public ProductLibraryPageQueryService(ProductLibraryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, ProductLibraryPageQuery query) {
        return applicationService.getSelectedLibraryPage(page, size, query);
    }

    public ProductLibraryCursorPage getSelectedLibraryCursorPage(
            String cursor,
            long limit,
            ProductLibraryPageQuery query) {
        return applicationService.getSelectedLibraryCursorPage(cursor, limit, query);
    }
}
