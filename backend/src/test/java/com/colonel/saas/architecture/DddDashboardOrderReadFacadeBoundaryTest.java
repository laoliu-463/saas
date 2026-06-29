package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddDashboardOrderReadFacadeBoundaryTest {

    @Test
    void dashboardServiceShouldReadOrderAggregatesThroughOrderReadFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/DashboardService.java"));

        assertThat(source)
                .contains("OrderReadFacade")
                .contains("orderReadFacade.getDashboardAttributionSummary")
                .contains("orderReadFacade.getDashboardFallbackSummary")
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("private final ColonelsettlementOrderMapper");
    }
}
