package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceAccessPolicyBoundaryTest {

    @Test
    void performanceAccessPolicy_shouldLiveInDomainPolicyPackage() throws IOException {
        Path mainSource = Path.of("src/main/java");

        boolean legacyImportExists;
        try (var files = Files.walk(mainSource)) {
            legacyImportExists = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(DddPerformanceAccessPolicyBoundaryTest::readUnchecked)
                    .anyMatch(source -> source.contains("com.colonel.saas.service.performance."));
        }

        assertThat(legacyImportExists)
                .as("performance access policy should not be consumed from service.performance")
                .isFalse();
        assertThat(Path.of(
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java"))
                .exists();
        assertThat(Path.of(
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessContext.java"))
                .exists();
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
