package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyPerformanceMetricsBoundaryTest {

    @Test
    void performanceMetricsQueryService_shouldDelegateDataScopeDecisionToUserPolicy() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("dataScopePolicy")
                .doesNotContain("switch (dataScope)");
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
