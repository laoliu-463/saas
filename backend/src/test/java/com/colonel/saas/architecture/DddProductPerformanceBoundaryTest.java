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

class DddProductPerformanceBoundaryTest {

    private static final Pattern ATTRIBUTION_OR_OWNERSHIP_SERVICE_REFERENCE = Pattern.compile(
            "\\b(?:AttributionService|OrderAttributionService|OrderAttributionReplayService|"
                    + "PerformanceAttributionPolicy|PerformanceCalculationService|PerformanceBackfillService|"
                    + "PerformanceRecordSyncListener)\\b");

    private static final Pattern COMMISSION_OR_PERFORMANCE_MONEY_REFERENCE = Pattern.compile(
            "\\b(?:CommissionService|CommissionRuleService|CommissionRuleMapper|"
                    + "PerformanceRecord|PerformanceRecordMapper|PerformanceMoneyPolicy)\\b");

    @Test
    void productOwnedCodeMustNotCallFinalAttributionOrOwnershipServices() throws IOException {
        assertNoForbiddenReferences(ATTRIBUTION_OR_OWNERSHIP_SERVICE_REFERENCE,
                "Product-owned code must not calculate final order attribution or ownership");
    }

    @Test
    void productOwnedCodeMustNotCallCommissionOrPerformanceMoneyServices() throws IOException {
        assertNoForbiddenReferences(COMMISSION_OR_PERFORMANCE_MONEY_REFERENCE,
                "Product-owned code must not calculate commission, performance records, or settlement money");
    }

    private static void assertNoForbiddenReferences(Pattern forbidden, String description) throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        for (Path path : productOwnedSourceFiles(sourceRoot)) {
            String source = Files.readString(path);
            if (forbidden.matcher(source).find()) {
                violations.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
            }
        }

        assertThat(violations)
                .as(description)
                .isEmpty();
    }

    private static List<Path> productOwnedSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DddProductPerformanceBoundaryTest::isProductOwnedSource)
                    .toList();
        }
    }

    private static boolean isProductOwnedSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        return normalized.contains("/domain/product/")
                || fileName.startsWith("Product")
                || fileName.equals("ColonelActivityProductController.java")
                || fileName.equals("AdminDouyinQuickSampleController.java");
    }
}
