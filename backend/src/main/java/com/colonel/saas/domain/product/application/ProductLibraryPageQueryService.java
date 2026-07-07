package com.colonel.saas.domain.product.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.entity.Product;
import com.colonel.saas.service.ProductService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Product library page query facade.
 */
@Service
public class ProductLibraryPageQueryService {

    private final ProductService productService;

    public ProductLibraryPageQueryService(@Lazy ProductService productService) {
        this.productService = productService;
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, ProductLibraryPageQuery query) {
        return productService.getSelectedLibraryPage(page, size, toLegacyFilter(query));
    }

    public ProductService.SelectedLibraryCursorPage getSelectedLibraryCursorPage(
            String cursor,
            long limit,
            ProductLibraryPageQuery query) {
        return productService.getSelectedLibraryCursorPage(cursor, limit, toLegacyFilter(query));
    }

    private ProductService.SelectedLibraryFilter toLegacyFilter(ProductLibraryPageQuery query) {
        ProductLibraryPageQuery safeQuery = query == null ? emptyQuery() : query;
        return new ProductService.SelectedLibraryFilter(
                safeQuery.keyword(),
                safeQuery.status(),
                safeQuery.shopKeyword(),
                safeQuery.categoryName(),
                safeQuery.categories(),
                safeQuery.activityId(),
                safeQuery.assigneeId(),
                safeQuery.serviceFee(),
                safeQuery.supportsAds(),
                safeQuery.salesRange(),
                safeQuery.promotionLink(),
                safeQuery.allianceStatus(),
                safeQuery.commission(),
                safeQuery.hasSample(),
                safeQuery.assignee(),
                safeQuery.systemTag(),
                safeQuery.decision(),
                safeQuery.partnerId(),
                safeQuery.partnerType(),
                safeQuery.sortBy(),
                safeQuery.goodsTags(),
                safeQuery.productTags(),
                safeQuery.colonelName(),
                safeQuery.published(),
                safeQuery.cooperationType(),
                safeQuery.livePriceMin(),
                safeQuery.livePriceMax(),
                safeQuery.commissionMin(),
                safeQuery.commissionMax(),
                safeQuery.sampleSalesMin(),
                safeQuery.sampleSalesMax(),
                safeQuery.materialDownload(),
                safeQuery.exclusivePrice(),
                safeQuery.productChain(),
                safeQuery.handCard(),
                safeQuery.doubleCommission(),
                safeQuery.notInLibrary(),
                safeQuery.dedup(),
                safeQuery.recruitActivityId(),
                safeQuery.recruitActivityName(),
                safeQuery.listed(),
                safeQuery.freeSample(),
                safeQuery.productId());
    }

    private ProductLibraryPageQuery emptyQuery() {
        return new ProductLibraryPageQuery(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
