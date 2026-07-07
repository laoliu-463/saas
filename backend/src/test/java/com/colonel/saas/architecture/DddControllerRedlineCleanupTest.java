package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddControllerRedlineCleanupTest {

    @Test
    void adminQuickSampleController_shouldNotImportLegacyDouyinQuickSampleGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/AdminDouyinQuickSampleController.java");

        assertThat(source)
                .contains("ProductQuickSampleStatusQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway");
    }

    @Test
    void colonelActivityProductController_shouldNotImportLegacyPromotionGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java");

        assertThat(source).doesNotContain("com.colonel.saas.gateway.douyin.DouyinPromotionGateway");
    }

    @Test
    void colonelActivityController_shouldNotImportLegacyActivityAndProductGateways() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/ColonelActivityController.java");

        assertThat(source)
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinActivityGateway")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinProductGateway");
    }

    @Test
    void colonelsettlementActivityController_shouldNotImportLegacyActivityGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/ColonelsettlementActivityController.java");

        assertThat(source)
                .contains("ColonelActivityDetailQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinActivityGateway");
    }

    @Test
    void douyinController_shouldNotImportLegacyTokenGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/DouyinController.java");

        assertThat(source)
                .contains("DouyinTokenDiagnosticQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinTokenGateway");
    }

    @Test
    void douyinController_shouldNotImportLegacyProductGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/DouyinController.java");

        assertThat(source)
                .contains("DouyinProductDiagnosticQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinProductGateway");
    }

    @Test
    void douyinController_shouldNotImportLegacyPromotionGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/DouyinController.java");

        assertThat(source)
                .contains("DouyinPromotionDiagnosticQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinPromotionGateway");
    }

    @Test
    void douyinController_shouldNotImportLegacyOrderGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/DouyinController.java");

        assertThat(source)
                .contains("DouyinOrderDiagnosticQueryService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinOrderGateway");
    }

    @Test
    void douyinController_shouldNotImportLegacyActivityGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/DouyinController.java");

        assertThat(source)
                .contains("DouyinActivityDiagnosticService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinActivityGateway");
    }

    @Test
    void productController_shouldNotImportLegacyPromotionGateway() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/ProductController.java");

        assertThat(source)
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinPromotionGateway");
    }

    @Test
    void orderController_shouldNotImportLegacyOrderMapper() throws Exception {
        String source = readSource("src/main/java/com/colonel/saas/controller/OrderController.java");

        assertThat(source)
                .contains("OrderReadFacade")
                .contains("OrderFilterOptionsQueryService")
                .doesNotContain("com.colonel.saas.mapper.ColonelsettlementOrderMapper");
    }

    private String readSource(String relativePath) throws Exception {
        Path sourcePath = Path.of(relativePath);
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend").resolve(relativePath);
        }
        return Files.readString(sourcePath);
    }
}
