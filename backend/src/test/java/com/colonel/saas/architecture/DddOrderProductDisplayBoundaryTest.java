package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderProductDisplayBoundaryTest {

    @Test
    void orderServiceShouldReadProductDisplayDataThroughProductDomainFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/OrderService.java"));

        assertThat(source)
                .doesNotContain("ProductMapper")
                .doesNotContain("ProductSnapshotMapper")
                .doesNotContain("Product::")
                .doesNotContain("ProductSnapshot::")
                .contains("ProductDomainFacade")
                .contains("loadOrderDisplaySnapshots")
                .contains("loadOrderDisplayProducts");
    }
}
