package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.application.port.CopyPromotionSupportPort;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyPromotionApplicationServiceTest {

    @Mock
    private CopyPromotionSupportPort copyPromotionSupportPort;
    @Mock
    private ConfigDomainFacade configDomainFacade;

    private CopyPromotionApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CopyPromotionApplicationService(
                copyPromotionSupportPort,
                configDomainFacade);
    }

    @Test
    void copyPromotion_shouldRenderFallbackTextWhenRealWriteDisabled() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId("P-1");
        snapshot.setTitle("测试商品");
        snapshot.setShopName("测试店铺");
        ProductOperationState state = new ProductOperationState();
        var context = new CopyPromotionSupportPort.Context(snapshot, state);
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any())).thenReturn(context);

        PromotionLinkCopyResult result = applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                false,
                false);

        assertThat(result.copyText()).isNotNull();
        assertThat(result.realPromotionWriteEnabled()).isFalse();
    }

    @Test
    void copyPromotionApplicationService_shouldNotDependOnLegacyProductServiceOrGateway() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("com.colonel.saas.service.ProductService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinPromotionGateway")
                .doesNotContain("@Lazy");
    }

    @Test
    void legacyProductService_shouldNotExposeCopyPromotionEntryPoint() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/service/ProductService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/service/ProductService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("generatePromotionLinkCopy(")
                .doesNotContain("CopyPromotionApplicationService");
    }
}
