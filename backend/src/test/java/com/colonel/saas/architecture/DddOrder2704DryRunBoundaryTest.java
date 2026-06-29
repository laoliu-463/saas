package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrder2704DryRunBoundaryTest {

    @Test
    void order2704SettlementDryRunShouldReadLocalOrdersThroughOrderReadFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/Order2704SettlementDryRunService.java"));

        assertThat(source)
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("QueryWrapper")
                .contains("OrderReadFacade")
                .contains("findActiveOrderIdsBySettleTimeRange");
    }
}
