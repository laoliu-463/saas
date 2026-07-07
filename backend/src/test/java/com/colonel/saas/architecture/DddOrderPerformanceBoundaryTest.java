package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderPerformanceBoundaryTest {

    private static final Pattern PERFORMANCE_MUTATION_REFERENCE = Pattern.compile(
            "\\b(?:CommissionService|PerformanceBackfillService|PerformanceRecordMapper|"
                    + "batchUpsertPerformanceRecords|reconcileInvalidatedPerformance)\\b");

    private static final Pattern PERFORMANCE_RECORDS_WRITE_SQL = Pattern.compile(
            "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+performance_records\\b");

    @Test
    void orderOwnedCodeMustNotCallPerformanceMutationServices() throws IOException {
        Set<String> violations = findViolations(PERFORMANCE_MUTATION_REFERENCE);

        assertThat(violations)
                .as("Order-owned code must publish/read order facts and leave commission or performance writes to performance-owned code")
                .isEmpty();
    }

    @Test
    void orderOwnedCodeMustNotWritePerformanceRecordsTable() throws IOException {
        Set<String> violations = findViolations(PERFORMANCE_RECORDS_WRITE_SQL);

        assertThat(violations)
                .as("Order-owned code may read performance_records for compatibility checks, but must not write it")
                .isEmpty();
    }

    @Test
    void legacyOrdersPerformanceEndpointsMustLiveInPerformanceOwnedBridge() throws IOException {
        Path sourceRoot = sourceRoot();
        String orderController = uncommented(Files.readString(
                sourceRoot.resolve("com/colonel/saas/controller/OrderController.java")));
        String bridgeController = uncommented(Files.readString(
                sourceRoot.resolve("com/colonel/saas/controller/PerformanceOrderAdminController.java")));

        assertThat(orderController)
                .doesNotContain(
                        "@PostMapping(\"/performance-backfill\")",
                        "@PostMapping(\"/performance-reconcile-invalidated\")",
                        "@PostMapping(\"/commission-batch\")",
                        "@PostMapping(\"/commission-recalculate\")");

        assertThat(bridgeController)
                .contains(
                        "@RequestMapping(\"/orders\")",
                        "@PostMapping(\"/performance-backfill\")",
                        "@PostMapping(\"/performance-reconcile-invalidated\")",
                        "@PostMapping(\"/commission-batch\")",
                        "@PostMapping(\"/commission-recalculate\")");
    }

    private static Set<String> findViolations(Pattern forbidden) throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path sourceRoot = sourceRoot();
        for (Path path : orderOwnedSourceFiles(sourceRoot)) {
            String source = uncommented(Files.readString(path));
            if (forbidden.matcher(source).find()) {
                violations.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
            }
        }
        return violations;
    }

    private static List<Path> orderOwnedSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DddOrderPerformanceBoundaryTest::isOrderOwnedSource)
                    .toList();
        }
    }

    private static boolean isOrderOwnedSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        return normalized.contains("/domain/order/")
                || normalized.contains("/service/Order")
                || normalized.contains("/job/Order")
                || fileName.equals("OrderController.java")
                || fileName.equals("OrderAttributionController.java")
                || fileName.equals("OrderSyncController.java");
    }

    private static Path sourceRoot() {
        return Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
    }

    private static String uncommented(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
