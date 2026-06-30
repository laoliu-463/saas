package com.colonel.saas.domain.product.application;

import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ProductLibraryApplicationService 单元测试（DDD-PRODUCT-001 Slice 1）。
 *
 * <p>原 ProductServiceFilterTest.listLibraryCategories_shouldReturnDistinctSortedNames
 * 业务断言已迁移到 Application；Service 委派壳为 1-line delegate，单独测试由
 * 集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductLibraryApplicationServiceTest {

    @Mock
    private ProductSnapshotMapper snapshotMapper;

    private ProductLibraryApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ProductLibraryApplicationService(snapshotMapper);
    }

    @Test
    void listLibraryCategories_shouldReturnDistinctSortedNames() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames())
                .thenReturn(List.of("美妆", "食品饮料", "美妆"));

        assertThat(applicationService.listLibraryCategories())
                .containsExactly("美妆", "食品饮料");
    }

    @Test
    void listLibraryCategories_shouldReturnEmptyListWhenMapperReturnsNull() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames()).thenReturn(null);

        assertThat(applicationService.listLibraryCategories()).isEmpty();
    }

    @Test
    void listLibraryCategories_shouldReturnEmptyListWhenMapperReturnsEmpty() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames()).thenReturn(List.of());

        assertThat(applicationService.listLibraryCategories()).isEmpty();
    }

    @Test
    void listLibraryCategories_shouldFilterBlankAndTrimValues() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames())
                .thenReturn(List.of("  美妆  ", "", "  ", "食品"));

        List<String> result = applicationService.listLibraryCategories();

        assertThat(result).containsExactly("美妆", "食品");
    }

    @Test
    void listLibraryCategories_shouldSortCaseInsensitive() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames())
                .thenReturn(List.of("banana", "Apple", "cherry"));

        assertThat(applicationService.listLibraryCategories())
                .containsExactly("Apple", "banana", "cherry");
    }
}