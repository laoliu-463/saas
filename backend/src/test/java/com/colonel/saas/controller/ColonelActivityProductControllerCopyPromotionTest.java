package com.colonel.saas.controller;

import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.service.ProductPinService;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelActivityProductControllerCopyPromotionTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductPinService productPinService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private CopyPromotionApplicationService copyPromotionApplicationService;

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(
                productService,
                productPinService,
                sysUserService,
                copyPromotionApplicationService);
    }

    @Test
    void generatePromotionLink_shouldDelegateCopyPromotionApplicationService() {
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
        when(copyPromotionApplicationService.generatePromotionLinkCopy(
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

        ColonelActivityProductController.PromotionLinkRequest request =
                new ColonelActivityProductController.PromotionLinkRequest();
        request.setExternalUniqueId("ext-1");
        request.setPromotionScene(4);
        request.setNeedShortLink(true);
        request.setScene("PRODUCT_LIBRARY");
        request.setTalentId("talent-1");

        var response = controller.generatePromotionLink(
                "ACT-1",
                "P-1",
                request,
                "idem-1",
                userId,
                deptId);

        assertThat(response.getData()).isSameAs(expected);
        verify(copyPromotionApplicationService).generatePromotionLinkCopy(
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
        verifyNoInteractions(productService);
    }
}
