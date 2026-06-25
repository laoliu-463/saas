package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD policy layer must stay framework-free so domain rules can be tested
 * without Spring wiring.
 */
class DddPolicyLayerNoSpringDependencyTest {

    @Test
    void policyLayerShouldNotImportSpringOrDeclareSpringComponents() throws Exception {
        Path domainRoot = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/colonel/saas/domain");

        try (Stream<Path> paths = Files.walk(domainRoot)) {
            List<String> violations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.getParent() != null
                            && "policy".equals(path.getParent().getFileName().toString()))
                    .flatMap(this::springViolations)
                    .sorted()
                    .toList();

            assertThat(violations)
                    .as("domain policy layer must not depend on Spring")
                    .isEmpty();
        }
    }

    private Stream<String> springViolations(Path path) {
        try {
            String content = Files.readString(path);
            String relative = Path.of(System.getProperty("user.dir")).relativize(path).toString();
            return Stream.of(
                    violation(relative, content, "import org.springframework."),
                    violation(relative, content, "@Component"),
                    violation(relative, content, "@Service"),
                    violation(relative, content, "@Repository")
            ).flatMap(List::stream);
        } catch (Exception ex) {
            return Stream.of(path + ": unreadable: " + ex.getMessage());
        }
    }

    private List<String> violation(String relative, String content, String marker) {
        return content.contains(marker) ? List.of(relative + " contains " + marker) : List.of();
    }
}
