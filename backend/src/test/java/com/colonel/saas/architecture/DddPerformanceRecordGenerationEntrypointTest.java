package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceRecordGenerationEntrypointTest {

    @Test
    void performanceRecordsShouldBeWrittenOnlyThroughCalculationApplicationUpsert() throws IOException {
        assertThat(mainJavaFilesContaining(Pattern.compile("\\bupsertFromOrder\\s*\\(")))
                .containsExactly(
                        "com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java",
                        "com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationService.java",
                        "com/colonel/saas/listener/PerformanceRecordSyncListener.java",
                        "com/colonel/saas/service/CommissionService.java",
                        "com/colonel/saas/service/PerformanceBackfillService.java",
                        "com/colonel/saas/service/PerformanceCalculationService.java");

        assertThat(mainJavaFilesContaining(Pattern.compile("\\bperformanceRecordMapper\\.upsert\\s*\\(")))
                .containsExactly("com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java");
    }

    @Test
    void rawPerformanceRecordWritesShouldStayInMapperXmlOnly() throws IOException {
        assertThat(resourceFilesContaining(Pattern.compile(
                "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+performance_records\\b")))
                .containsExactly("mapper/PerformanceRecordMapper.xml");

        assertThat(mainJavaFilesContaining(Pattern.compile(
                "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+performance_records\\b")))
                .isEmpty();

        assertSourceContains(
                "src/main/resources/mapper/PerformanceRecordMapper.xml",
                "<insert id=\"upsert\"",
                "INSERT INTO performance_records",
                "ON CONFLICT (order_id) DO UPDATE",
                "calculation_version = performance_records.calculation_version + 1");
    }

    @Test
    void eventAndManualEntrypointsShouldDelegateIntoTheSameGenerationFunnel() throws IOException {
        assertSourceContains(
                "src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java",
                "@EventListener",
                "OrderReadFacade",
                "performanceCalculationApplicationService.upsertFromOrder(order)",
                "PerformanceCalculatedEvent");
        assertSourceContains(
                "src/main/java/com/colonel/saas/service/PerformanceBackfillService.java",
                "public BackfillResult backfill(",
                "public BackfillResult reconcileInvalidatedPerformance(Integer limit)",
                "performanceCalculationApplicationService.upsertFromOrder(order)");
        assertSourceContains(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationService.java",
                "findUnsettledOrdersByCreateTimeRange",
                "performanceCalculationApplicationService.upsertFromOrder(order)");
        assertSourceContains(
                "src/main/java/com/colonel/saas/service/CommissionService.java",
                "public List<OrderCommissionItem> batchUpsertPerformanceRecords",
                "performanceCalculationService.upsertFromOrder(order)");
    }

    @Test
    void controllersAndJobsShouldExposeOnlyDocumentedGenerationEntrypoints() throws IOException {
        assertSourceContains(
                "src/main/java/com/colonel/saas/controller/PerformanceOrderAdminController.java",
                "@PostMapping(\"/performance-backfill\")",
                "performanceBackfillService.backfill(",
                "@PostMapping(\"/performance-reconcile-invalidated\")",
                "performanceBackfillService.reconcileInvalidatedPerformance",
                "@PostMapping(\"/commission-batch\")",
                "commissionService.batchUpsertPerformanceRecords",
                "@PostMapping(\"/commission-recalculate\")");
        assertSourceContains(
                "src/main/java/com/colonel/saas/controller/PerformanceController.java",
                "monthRecalculationService.recalculateMonth(");
        assertSourceContains(
                "src/main/java/com/colonel/saas/job/PerformanceBackfillJob.java",
                "@Scheduled(cron = \"0 30 3 * * ?\")",
                "performanceBackfillService.backfill(");
        assertSourceContains(
                "src/main/java/com/colonel/saas/job/PerformanceRecalculateFailedJob.java",
                "@Scheduled(cron = \"${performance.recalculate-failed.cron:0 */10 * * * ?}\")",
                "performanceBackfillService.backfill(");
    }

    private static List<String> mainJavaFilesContaining(Pattern pattern) throws IOException {
        return filesContaining(sourceRoot(), ".java", pattern);
    }

    private static List<String> resourceFilesContaining(Pattern pattern) throws IOException {
        return filesContaining(resourceRoot(), null, pattern);
    }

    private static List<String> filesContaining(Path root, String suffix, Pattern pattern) throws IOException {
        Set<String> matches = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> suffix == null || path.toString().endsWith(suffix))
                    .forEach(path -> {
                        try {
                            String source = suffix != null && suffix.equals(".java")
                                    ? uncommented(Files.readString(path))
                                    : Files.readString(path);
                            if (pattern.matcher(source).find()) {
                                matches.add(root.relativize(path).toString().replace('\\', '/'));
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }
        return List.copyOf(matches);
    }

    private static void assertSourceContains(String relativePath, String... fragments) throws IOException {
        String source = Files.readString(projectFile(relativePath)).replace("\r\n", "\n");
        assertThat(source)
                .as(relativePath)
                .contains(fragments);
    }

    private static String uncommented(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static Path projectFile(String relativePath) {
        return Path.of(System.getProperty("user.dir")).resolve(relativePath);
    }

    private static Path sourceRoot() {
        return projectFile("src/main/java");
    }

    private static Path resourceRoot() {
        return projectFile("src/main/resources");
    }
}
