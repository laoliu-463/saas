package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyPerformanceMetricsBoundaryTest {

    @Test
    void performanceMetricsQueryService_shouldDelegateDataScopeDecisionToUserPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("dataScopePolicy")
                .doesNotContain("switch (dataScope)");
    }
}
