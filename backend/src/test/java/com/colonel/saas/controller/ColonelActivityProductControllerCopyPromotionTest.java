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

import java.nio.file.Files;
import java.nio.file.Path;
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
    private CopyPromotionApplicationService copyPromotionApplicationService;
    @Mock
    private ProductPinService productPinService;
    @Mock
    private SysUserService sysUserService;

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(
                productService,
                copyPromotionApplicationService,
                productPinService,
                sysUserService);
    }

    @Test
    void generatePromotionLink_shouldDelegateCopyPromotionApplicationService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult expected =
                new com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult(
                "copy text",
                true,
                "https://s.link",
                "PS-1",
                null,
                true,
                true);
        when(copyPromotionApplicationService.copyPromotion(
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
        verify(copyPromotionApplicationService).copyPromotion(
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

    @Test
    void generatePromotionLink_shouldRouteThroughCopyPromotionApplicationService() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CopyPromotionApplicationService")
                .doesNotContain("productService.generatePromotionLinkCopy");
    }
}
