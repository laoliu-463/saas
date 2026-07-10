package com.colonel.saas.domain.product.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLibraryPageQueryServiceTest {

    private final ProductLibraryApplicationService applicationService = mock(ProductLibraryApplicationService.class);
    private final ProductLibraryPageQueryService service = new ProductLibraryPageQueryService(applicationService);

    @Test
    void getSelectedLibraryPage_shouldDelegateToApplicationService() {
        Page<Product> expected = new Page<>(1, 20);
        expected.setRecords(List.of(new Product()));
        when(applicationService.getSelectedLibraryPage(1L, 20L, query()))
                .thenReturn(expected);

        var result = service.getSelectedLibraryPage(1, 20, query());

        assertThat(result).isSameAs(expected);
        verify(applicationService).getSelectedLibraryPage(1L, 20L, query());
    }

    @Test
    void getSelectedLibraryCursorPage_shouldDelegateToApplicationService() {
        Product product = new Product();
        product.setProductId("9001");
        ProductLibraryCursorPage expected =
                new ProductLibraryCursorPage(List.of(product), 500L, true, "next");
        when(applicationService.getSelectedLibraryCursorPage("cursor", 500L, query()))
                .thenReturn(expected);

        var result = service.getSelectedLibraryCursorPage("cursor", 500, query());

        assertThat(result).isSameAs(expected);
        verify(applicationService).getSelectedLibraryCursorPage("cursor", 500L, query());
    }

    @Test
    void nullQuery_shouldUseEmptyApplicationQuery() {
        Page<Product> expected = new Page<>(1, 20);
        when(applicationService.getSelectedLibraryPage(1L, 20L, null))
                .thenReturn(expected);

        service.getSelectedLibraryPage(1, 20, null);

        verify(applicationService).getSelectedLibraryPage(1L, 20L, null);
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
