package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-CLEAN-004: keep product-owned code off direct sample domain packages.
 */
class DddClean004ProductSampleDependencyGuardTest {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern TYPE_NAME =
            Pattern.compile("(?:public\\s+)?(?:class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern SAMPLE_DOMAIN_IMPORT =
            Pattern.compile("^import\\s+com\\.colonel\\.saas\\.domain\\.sample\\.", Pattern.MULTILINE);
    private static final Pattern SAMPLE_FACT_OR_STATUS_REFERENCE = Pattern.compile(
            "\\b(?:SampleApplicationService|SampleStatusLogService|SampleRequestMapper|SampleStatusLogMapper|"
                    + "SampleLifecycleService|SampleLogisticsImportService|SampleLogisticsSyncService|"
                    + "SampleLogisticsSubscriptionService|SampleRequest|SampleStatusLog)\\b"
                    + "|\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+sample_(?:request|status_log)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Test
    void productOwnedClassesMustNotImportSampleDomainPackages() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> violations = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(backendRoot.resolve("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(path);
                String fqcn = readFqcn(content);
                if (fqcn == null || MapperDomainRegistry.ownerDomain(fqcn) != MapperDomainRegistry.Domain.PRODUCT) {
                    continue;
                }
                if (fqcn.contains(".domain.product.infrastructure.")) {
                    continue;
                }
                if (SAMPLE_DOMAIN_IMPORT.matcher(content).find()) {
                    violations.add(fqcn);
                }
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-004: product code must use ProductSampleApplicationPort, not domain.sample imports")
                .isEmpty();
    }

    @Test
    void productOwnedCodeMustNotWriteSampleFactsOrStatusesDirectly() throws IOException {
        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        Set<String> violations = new LinkedHashSet<>();
        for (Path path : productOwnedSourceFiles(sourceRoot)) {
            String content = Files.readString(path);
            if (SAMPLE_FACT_OR_STATUS_REFERENCE.matcher(content).find()) {
                violations.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-004: product-owned code must delegate sample application/status writes through ProductSampleApplicationPort")
                .isEmpty();
    }

    private static String readFqcn(String content) {
        Matcher packageMatcher = PACKAGE.matcher(content);
        Matcher typeMatcher = TYPE_NAME.matcher(content);
        if (!packageMatcher.find() || !typeMatcher.find()) {
            return null;
        }
        return packageMatcher.group(1) + "." + typeMatcher.group(1);
    }

    private static List<Path> productOwnedSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DddClean004ProductSampleDependencyGuardTest::isProductOwnedSource)
                    .toList();
        }
    }

    private static boolean isProductOwnedSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        if (normalized.contains("/domain/product/infrastructure/")) {
            return false;
        }
        return normalized.contains("/domain/product/")
                || fileName.startsWith("Product")
                || fileName.equals("ColonelActivityProductController.java")
                || fileName.equals("AdminDouyinQuickSampleController.java");
    }
}
