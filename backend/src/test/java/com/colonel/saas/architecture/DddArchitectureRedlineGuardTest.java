package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD100-GUARD: executable redline scans for architecture debt that must not grow.
 */
class DddArchitectureRedlineGuardTest {

    private static final Pattern CONTROLLER_REDLINE_IMPORT = Pattern.compile(
            "^import\\s+(com\\.colonel\\.saas\\.(?:mapper|gateway)\\.[\\w.]+);",
            Pattern.MULTILINE);
    private static final Pattern DOMAIN_MAPPER_IMPORT = Pattern.compile(
            "^import\\s+com\\.colonel\\.saas\\.mapper\\.",
            Pattern.MULTILINE);
    private static final Pattern FRONTEND_DIRECT_THIRD_PARTY_HTTP = Pattern.compile(
            "(?:fetch|axios\\.(?:get|post|put|delete|request)|request\\.(?:get|post|put|delete))"
                    + "\\s*\\(\\s*['\"]https?://|axios\\.create\\s*\\([^\\n]*baseURL\\s*:\\s*['\"]https?://");

    @Test
    void controllerPersistenceAndGatewayImportsMustNotGrowBeyondLegacyWhitelist() throws IOException {
        Set<String> current = scanControllerRedlineImports();
        Set<String> legacy = loadLegacyWhitelist();

        Set<String> newViolations = new LinkedHashSet<>(current);
        newViolations.removeAll(legacy);

        assertThat(newViolations)
                .as("Controllers must not add direct Mapper/Gateway dependencies. "
                        + "Move orchestration to application/facade/port layers instead.")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustReflectCurrentControllerDebt() throws IOException {
        Set<String> current = scanControllerRedlineImports();
        Set<String> legacy = loadLegacyWhitelist();

        Set<String> removedDebt = new LinkedHashSet<>(legacy);
        removedDebt.removeAll(current);

        assertThat(removedDebt)
                .as("Controller redline whitelist contains entries that no longer exist. "
                        + "Remove retired debt from ddd/architecture-redline-legacy-whitelist.txt.")
                .isEmpty();
    }

    @Test
    void domainPolicyQueryPortAndApiLayersMustNotImportMyBatisMappers() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path domainRoot = backendRoot().resolve("src/main/java/com/colonel/saas/domain");
        try (Stream<Path> paths = Files.walk(domainRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(DddArchitectureRedlineGuardTest::isStrictDomainLayer)
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (DOMAIN_MAPPER_IMPORT.matcher(source).find()) {
                                violations.add(relativeBackendPath(path));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }

        assertThat(violations)
                .as("domain api/query/policy/port layers must not import MyBatis mappers; use ports/adapters")
                .isEmpty();
    }

    @Test
    void frontendSourcesMustNotCallThirdPartyHttpDirectly() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path frontendRoot = repoRoot().resolve("frontend/src");
        try (Stream<Path> paths = Files.walk(frontendRoot)) {
            paths.filter(DddArchitectureRedlineGuardTest::isFrontendSource)
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (FRONTEND_DIRECT_THIRD_PARTY_HTTP.matcher(source).find()) {
                                violations.add(repoRoot().relativize(path).toString().replace('\\', '/'));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }

        assertThat(violations)
                .as("frontend must call backend API clients, not third-party HTTP endpoints directly")
                .isEmpty();
    }

    private Set<String> scanControllerRedlineImports() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        Path mainJava = backendRoot().resolve("src/main/java");
        try (Stream<Path> paths = Files.walk(mainJava)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(DddArchitectureRedlineGuardTest::isControllerSource)
                    .forEach(path -> {
                        try {
                            Matcher matcher = CONTROLLER_REDLINE_IMPORT.matcher(Files.readString(path));
                            while (matcher.find()) {
                                result.add(relativeBackendPath(path) + "|" + matcher.group(1));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }
        return result;
    }

    private Set<String> loadLegacyWhitelist() throws IOException {
        Set<String> lines = new LinkedHashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/ddd/architecture-redline-legacy-whitelist.txt")) {
            assertThat(in).as("architecture redline legacy whitelist resource").isNotNull();
            String text = new String(in.readAllBytes());
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        }
        return lines;
    }

    private static boolean isControllerSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/com/colonel/saas/controller/")
                || normalized.contains("/com/colonel/saas/auth/controller/");
    }

    private static boolean isStrictDomainLayer(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/domain/") && (normalized.contains("/api/")
                || normalized.contains("/query/")
                || normalized.contains("/policy/")
                || normalized.contains("/port/"));
    }

    private static boolean isFrontendSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return (normalized.endsWith(".ts") || normalized.endsWith(".vue"))
                && !normalized.endsWith(".test.ts")
                && !normalized.endsWith(".d.ts");
    }

    private static String relativeBackendPath(Path path) {
        return backendRoot().relativize(path).toString().replace('\\', '/');
    }

    private static Path backendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("src/main/java"))) {
            return cwd;
        }
        return cwd.resolve("backend");
    }

    private static Path repoRoot() {
        Path backend = backendRoot();
        if (backend.getFileName() != null && "backend".equals(backend.getFileName().toString())) {
            return backend.getParent();
        }
        return backend;
    }
}
