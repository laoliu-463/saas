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

class DddPerformanceOrderBoundaryContractTest {

    private static final Pattern ORDER_SYNC_OR_UPSTREAM_REFERENCE = Pattern.compile(
            "\\b(?:OrderSyncService|OrderSyncApplicationService|OrderSyncPersistenceService|"
                    + "OrderSyncJob|DouyinOrderGateway|RealDouyinOrderGateway|OrderApi|"
                    + "InstituteOrderColonelSettlementGateway|buyin\\.instituteOrderColonel|"
                    + "buyin\\.colonelMultiSettlementOrders)\\b");

    private static final Pattern ORDER_FACT_WRITE_SQL = Pattern.compile(
            "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+colonelsettlement_order\\b");

    private static final Pattern ORDER_FACT_WRITE_MAPPER_REFERENCE = Pattern.compile(
            "\\b(?:ColonelsettlementOrderMapper|OrderSyncDedupClaimMapper)\\b");

    @Test
    void performanceOwnedCodeMustNotSynchronizeDouyinOrders() throws IOException {
        Set<String> violations = findViolations(ORDER_SYNC_OR_UPSTREAM_REFERENCE);

        assertThat(violations)
                .as("Y-7: performance-owned code must consume order facts, not call order sync jobs or Douyin order gateways")
                .isEmpty();
    }

    @Test
    void performanceOwnedCodeMustNotWriteOrderFacts() throws IOException {
        Set<String> violations = findViolations(ORDER_FACT_WRITE_SQL, ORDER_FACT_WRITE_MAPPER_REFERENCE);

        assertThat(violations)
                .as("Y-8: performance-owned code may read colonelsettlement_order facts but must not mutate order facts")
                .isEmpty();
    }

    @Test
    void performanceOwnedCodeShouldKeepOrderFactsReadOnlyEntrypointsDiscoverable() throws IOException {
        assertSourceContains(
                "com/colonel/saas/listener/PerformanceRecordSyncListener.java",
                "OrderReadFacade",
                "performanceCalculationApplicationService.upsertFromOrder(order)");
        assertSourceContains(
                "com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationService.java",
                "OrderReadFacade",
                "findUnsettledOrdersByCreateTimeRange");
        assertSourceContains(
                "com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java",
                "FROM colonelsettlement_order co",
                "LEFT JOIN performance_records pr ON pr.order_id = co.order_id");
        assertSourceContains(
                "com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java",
                "FROM colonelsettlement_order co",
                "LEFT JOIN performance_records pr ON pr.order_id = co.order_id");
    }

    private static Set<String> findViolations(Pattern... forbiddenPatterns) throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path sourceRoot = sourceRoot();
        for (Path path : performanceOwnedSourceFiles(sourceRoot)) {
            String source = uncommented(Files.readString(path));
            for (Pattern forbiddenPattern : forbiddenPatterns) {
                if (forbiddenPattern.matcher(source).find()) {
                    violations.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
                    break;
                }
            }
        }
        return violations;
    }

    private static List<Path> performanceOwnedSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DddPerformanceOrderBoundaryContractTest::isPerformanceOwnedSource)
                    .toList();
        }
    }

    private static boolean isPerformanceOwnedSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        return normalized.contains("/domain/performance/")
                || (normalized.contains("/service/") && fileName.startsWith("Performance"))
                || (normalized.contains("/service/") && fileName.startsWith("Commission"))
                || fileName.equals("OrderCommissionPolicy.java")
                || (normalized.contains("/listener/") && fileName.startsWith("Performance"))
                || (normalized.contains("/controller/") && fileName.startsWith("Performance"));
    }

    private static void assertSourceContains(String relativePath, String... fragments) throws IOException {
        String source = Files.readString(sourceRoot().resolve(relativePath)).replace("\r\n", "\n");
        assertThat(source)
                .as(relativePath)
                .contains(fragments);
    }

    private static Path sourceRoot() {
        return Path.of(System.getProperty("user.dir")).resolve("src/main/java");
    }

    private static String uncommented(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
