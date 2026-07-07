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

class DddOrderSampleHomeworkBoundaryTest {

    private static final Pattern SAMPLE_MUTATION_DEPENDENCY = Pattern.compile(
            "\\b(?:SampleLifecycleService|SampleRequestMapper|SampleStatusLogService|"
                    + "SampleStatusLogMapper|SampleRequest|SampleStatusLog)\\b");

    private static final Pattern SAMPLE_TABLE_WRITE_SQL = Pattern.compile(
            "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+sample_(?:request|status_log)\\b");

    @Test
    void orderOwnedCodeMustNotDirectlyMutateSampleHomeworkState() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path sourceRoot = sourceRoot();
        for (Path path : orderOwnedSourceFiles(sourceRoot)) {
            String source = uncommented(Files.readString(path));
            if (SAMPLE_MUTATION_DEPENDENCY.matcher(source).find()
                    || SAMPLE_TABLE_WRITE_SQL.matcher(source).find()) {
                violations.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
            }
        }

        assertThat(violations)
                .as("Order-owned code must publish order facts or call the sample homework facade, not mutate sample state")
                .isEmpty();
    }

    @Test
    void orderPersistenceAndEventBridgeMustUseSampleHomeworkFacade() throws IOException {
        Path sourceRoot = sourceRoot();
        String persistence = uncommented(Files.readString(
                sourceRoot.resolve("com/colonel/saas/service/OrderSyncPersistenceService.java")));
        String bridge = uncommented(Files.readString(
                sourceRoot.resolve("com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java")));

        assertThat(persistence)
                .contains("SampleHomeworkFacade")
                .contains("sampleHomeworkFacade.completePendingHomeworkByOrder")
                .doesNotContain("SampleLifecycleService");
        assertThat(bridge)
                .contains("SampleHomeworkFacade")
                .contains("sampleHomeworkFacade.completePendingHomeworkByOrder")
                .doesNotContain("SampleLifecycleService");
    }

    private static List<Path> orderOwnedSourceFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DddOrderSampleHomeworkBoundaryTest::isOrderOwnedSource)
                    .toList();
        }
    }

    private static boolean isOrderOwnedSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        return normalized.contains("/domain/order/")
                || fileName.startsWith("Order")
                || fileName.equals("OrderController.java")
                || fileName.equals("OrderAttributionController.java");
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
