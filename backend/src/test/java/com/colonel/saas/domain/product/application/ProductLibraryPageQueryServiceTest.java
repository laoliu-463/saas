package com.colonel.saas.domain.product.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.entity.Product;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLibraryPageQueryServiceTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductLibraryPageQueryService service = new ProductLibraryPageQueryService(productService);

    @Test
    void getSelectedLibraryPage_shouldTranslateQueryToLegacyFilter() {
        Page<Product> expected = new Page<>(1, 20);
        expected.setRecords(List.of(new Product()));
        when(productService.getSelectedLibraryPage(eq(1L), eq(20L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        var result = service.getSelectedLibraryPage(1, 20, query());

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ProductService.SelectedLibraryFilter> captor =
                ArgumentCaptor.forClass(ProductService.SelectedLibraryFilter.class);
        verify(productService).getSelectedLibraryPage(eq(1L), eq(20L), captor.capture());
        ProductService.SelectedLibraryFilter filter = captor.getValue();
        assertThat(filter.keyword()).isEqualTo("keyword");
        assertThat(filter.productId()).isEqualTo("9001");
        assertThat(filter.assigneeId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(filter.partnerType()).isEqualTo("COLONEL");
        assertThat(filter.listed()).isEqualTo("1");
    }

    @Test
    void getSelectedLibraryCursorPage_shouldTranslateQueryToLegacyFilter() {
        Product product = new Product();
        product.setProductId("9001");
        ProductService.SelectedLibraryCursorPage expected =
                new ProductService.SelectedLibraryCursorPage(List.of(product), 500L, true, "next");
        when(productService.getSelectedLibraryCursorPage(eq("cursor"), eq(500L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        var result = service.getSelectedLibraryCursorPage("cursor", 500, query());

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ProductService.SelectedLibraryFilter> captor =
                ArgumentCaptor.forClass(ProductService.SelectedLibraryFilter.class);
        verify(productService).getSelectedLibraryCursorPage(eq("cursor"), eq(500L), captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo("9001");
    }

    @Test
    void nullQuery_shouldUseEmptyLegacyFilter() {
        Page<Product> expected = new Page<>(1, 20);
        when(productService.getSelectedLibraryPage(eq(1L), eq(20L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        service.getSelectedLibraryPage(1, 20, null);

        ArgumentCaptor<ProductService.SelectedLibraryFilter> captor =
                ArgumentCaptor.forClass(ProductService.SelectedLibraryFilter.class);
        verify(productService).getSelectedLibraryPage(eq(1L), eq(20L), captor.capture());
        assertThat(captor.getValue().productId()).isNull();
        assertThat(captor.getValue().keyword()).isNull();
    }

    private ProductLibraryPageQuery query() {
        return new ProductLibraryPageQuery(
                "keyword",
                1,
                "shop",
                "category",
                "cat-a,cat-b",
                "activity-1",
                "11111111-1111-1111-1111-111111111111",
                "gt20",
                "1",
                "100_999",
                "LINKED",
                "promoting",
                "10_20",
                "1",
                "assigned",
                "traffic",
                "MAIN",
                "partner-1",
                "COLONEL",
                "latest",
                "tag-a",
                "tag-b",
                "colonel",
                "1",
                "sample",
                "10",
                "100",
                "20",
                "30",
                "1000",
                "2000",
                "1",
                "1",
                "1",
                "1",
                "1",
                "0",
                "1",
                "activity-2",
                "recruit",
                "1",
                "1",
                "9001");
    }
}
