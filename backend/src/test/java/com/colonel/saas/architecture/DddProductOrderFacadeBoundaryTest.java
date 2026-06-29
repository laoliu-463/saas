package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddProductOrderFacadeBoundaryTest {

    @Test
    void productServiceShouldReadOrderFactsThroughOrderReadFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/ProductService.java"));

        assertThat(source)
                .contains("OrderReadFacade")
                .contains("summarizeProductOrdersByActivity")
                .contains("findProductIdsByColonelBuyinId")
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("private final ColonelsettlementOrderMapper");
    }
}
