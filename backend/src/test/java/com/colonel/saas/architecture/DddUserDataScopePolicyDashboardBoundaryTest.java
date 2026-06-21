package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDashboardBoundaryTest {

    @Test
    void dashboardService_shouldDelegateDataScopeDecisionToUserPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/DashboardService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("dataScopePolicy")
                .doesNotContain("switch (dataScope)");
    }
}
