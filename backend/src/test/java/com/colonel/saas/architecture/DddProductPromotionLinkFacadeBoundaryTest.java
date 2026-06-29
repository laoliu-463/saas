package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddProductPromotionLinkFacadeBoundaryTest {

    @Test
    void productServiceShouldUsePromotionLinkRecordFacadeInsteadOfMapper() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/ProductService.java"));

        assertThat(source)
                .contains("PromotionLinkRecordFacade")
                .doesNotContain("PromotionLinkMapper")
                .doesNotContain("private final PromotionLinkMapper");
    }
}
