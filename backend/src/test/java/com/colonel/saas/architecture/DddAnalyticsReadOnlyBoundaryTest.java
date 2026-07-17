package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DddAnalyticsReadOnlyBoundaryTest {

    private static final List<String> ANALYTICS_READ_SIDE_SOURCE_FILES = List.of(
            "src/main/java/com/colonel/saas/controller/DashboardController.java",
            "src/main/java/com/colonel/saas/service/DashboardService.java",
            "src/main/java/com/colonel/saas/service/data/DataApplicationService.java",
            "src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java",
            "src/main/java/com/colonel/saas/service/DashboardShadowCompareService.java"
    );

    private static final Pattern BUSINESS_FACT_WRITE_INVOCATION = Pattern.compile(
            "\\.(insert|update|delete|deleteById|updateById|insertBatch|batchUpdate)\\s*\\(");
    private static final Pattern BUSINESS_FACT_WRITE_SQL = Pattern.compile(
            "\\b(insert\\s+into|update\\s+[a-z_][\\w.]*\\s+set|delete\\s+from|truncate\\s+table|drop\\s+table)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)//.*$");

    @Test
    void analyticsSources_shouldNotWriteBusinessFacts() throws IOException {
        for (String sourceFile : ANALYTICS_READ_SIDE_SOURCE_FILES) {
            String source = readExecutableSource(sourceFile);

            assertThat(BUSINESS_FACT_WRITE_INVOCATION.matcher(source).find())
                    .as("%s must stay read-only and not write order/sample/performance facts", sourceFile)
                    .isFalse();
            assertThat(BUSINESS_FACT_WRITE_SQL.matcher(source).find())
                    .as("%s must not embed SQL that writes order/sample/performance facts", sourceFile)
                    .isFalse();
        }
    }

    @Test
    void analyticsSources_shouldNotInvokeBusinessCommandOrRecalculationServices() throws IOException {
        for (String sourceFile : ANALYTICS_READ_SIDE_SOURCE_FILES) {
            String source = readExecutableSource(sourceFile);

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

    @Test
    void analyticsReadOnlyReview_shouldCoverDashboardReadSideSources() {
        assertThat(ANALYTICS_READ_SIDE_SOURCE_FILES)
                .contains(
                        "src/main/java/com/colonel/saas/controller/DashboardController.java",
                        "src/main/java/com/colonel/saas/service/DashboardService.java",
                        "src/main/java/com/colonel/saas/service/data/DataApplicationService.java",
                        "src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java",
                        "src/main/java/com/colonel/saas/service/DashboardShadowCompareService.java")
                .doesNotContain(
                        "src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java");
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }

    private static String readExecutableSource(String relativePath) throws IOException {
        String source = Files.readString(sourcePath(relativePath));
        String withoutBlockComments = BLOCK_COMMENT.matcher(source).replaceAll("");
        return LINE_COMMENT.matcher(withoutBlockComments).replaceAll("");
    }
}
