package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DddAnalyticsReadOnlyBoundaryTest {

    private static final List<String> ANALYTICS_SOURCE_FILES = List.of(
            "src/main/java/com/colonel/saas/service/DashboardService.java",
            "src/main/java/com/colonel/saas/service/data/DataApplicationService.java",
            "src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java"
    );

    private static final Pattern BUSINESS_FACT_WRITE_INVOCATION = Pattern.compile(
            "\\.(insert|update|delete|deleteById|updateById|insertBatch|batchUpdate)\\s*\\(");

    @Test
    void analyticsSources_shouldNotWriteBusinessFacts() throws IOException {
        for (String sourceFile : ANALYTICS_SOURCE_FILES) {
            String source = Files.readString(sourcePath(sourceFile));

            assertThat(BUSINESS_FACT_WRITE_INVOCATION.matcher(source).find())
                    .as("%s must stay read-only and not write order/sample/performance facts", sourceFile)
                    .isFalse();
        }
    }

    @Test
    void analyticsSources_shouldNotInvokeBusinessCommandOrRecalculationServices() throws IOException {
        for (String sourceFile : ANALYTICS_SOURCE_FILES) {
            String source = Files.readString(sourcePath(sourceFile));

            assertThat(source)
                    .as("%s must consume read models/facts without running command-side business flows", sourceFile)
                    .doesNotContain("OrderSyncService")
                    .doesNotContain("OrderAttributionReplayService")
                    .doesNotContain("PerformanceCalculationService")
                    .doesNotContain("PerformanceBackfillService")
                    .doesNotContain("SampleApplicationService")
                    .doesNotContain("SampleCommandService")
                    .doesNotContain("SampleLifecycleService")
                    .doesNotContain("ProductQuickSampleService")
                    .doesNotContain("upsertFromOrder(");
        }
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
