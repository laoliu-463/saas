package com.colonel.saas.domain.product.application;

import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyPromotionApplicationServiceTest {

    @Mock
    private ProductService productService;

    private CopyPromotionApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CopyPromotionApplicationService(productService);
    }

    @Test
    void generatePromotionLinkCopy_shouldDelegateLegacyProductServiceWithoutChangingContract() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductService.PromotionLinkCopyResult expected = new ProductService.PromotionLinkCopyResult(
                "copy text",
                true,
                "https://s.link",
                "PS-1",
                null,
                true,
                true);
        when(productService.generatePromotionLinkCopy(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1")).thenReturn(expected);

        ProductService.PromotionLinkCopyResult result = applicationService.generatePromotionLinkCopy(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1");

        assertThat(result).isSameAs(expected);
        verify(productService).generatePromotionLinkCopy(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1");
    }
}
