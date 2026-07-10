package com.colonel.saas.domain.product.infrastructure;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.domain.product.application.port.ProductLibraryQueryPort;
import com.colonel.saas.entity.Product;
import com.colonel.saas.service.ProductService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 商品库查询端口的 Legacy 实现。
 *
 * <p>这是迁移过渡适配器：HTTP/Application 边界已经稳定，实际筛选编排仍暂时由
 * {@link ProductService} 提供。后续迁移只替换本类，不改 Controller 和应用层契约。</p>
 */
@Service
public class LegacyProductLibraryQueryAdapter implements ProductLibraryQueryPort {

    private final ProductService productService;

    public LegacyProductLibraryQueryAdapter(@Lazy ProductService productService) {
        this.productService = productService;
    }

    @Override
    public IPage<Product> getSelectedLibraryPage(long page, long size, ProductLibraryPageQuery query) {
        return productService.getSelectedLibraryPage(page, size, toLegacyFilter(query));
    }

    @Override
    public ProductLibraryCursorPage getSelectedLibraryCursorPage(
            String cursor,
            long limit,
            ProductLibraryPageQuery query) {
        ProductService.SelectedLibraryCursorPage result = productService.getSelectedLibraryCursorPage(
                cursor,
                limit,
                toLegacyFilter(query));
        if (result == null) {
            return null;
        }
        return new ProductLibraryCursorPage(result.records(), result.limit(), result.hasMore(), result.nextCursor());
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
