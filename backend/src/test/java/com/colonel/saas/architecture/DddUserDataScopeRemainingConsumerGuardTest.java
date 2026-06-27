package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD100-USER-DATASCOPE: freeze remaining direct data-scope consumers outside the user domain.
 */
class DddUserDataScopeRemainingConsumerGuardTest {

    private static final Pattern DIRECT_SCOPE_LOGIC = Pattern.compile(
            "dataScope\\s*(==|!=)|DataScope\\.(PERSONAL|DEPT|ALL)|"
                    + "switch\\s*\\(\\s*dataScope\\s*\\)|case\\s+(PERSONAL|DEPT|ALL)");
    private static final Set<String> POLICY_EXEMPTIONS = Set.of(
            "domain/performance/policy/PerformanceAccessContext.java",
            "job/PerformanceCacheWarmupJob.java"
    );

    @Test
    void nonUserDomainDirectDataScopeConsumersMustNotGrow() throws IOException {
        Set<String> current = scanDirectDataScopeConsumers();
        Set<String> legacy = loadLegacyWhitelist();

        Set<String> newConsumers = new LinkedHashSet<>(current);
        newConsumers.removeAll(legacy);

        assertThat(newConsumers)
                .as("New direct self/group/all data-scope consumers are forbidden. "
                        + "Use user-domain DataScopePolicy and add targeted parity tests.")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustReflectCurrentDirectDataScopeConsumers() throws IOException {
        Set<String> current = scanDirectDataScopeConsumers();
        Set<String> legacy = loadLegacyWhitelist();

        Set<String> retired = new LinkedHashSet<>(legacy);
        retired.removeAll(current);

        assertThat(retired)
                .as("Data-scope direct consumer whitelist contains retired entries; remove them")
                .isEmpty();
    }

    @Test
    void remainingBusinessConsumersMustExposeUserPolicyPath() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        for (String relativePath : scanDirectDataScopeConsumers()) {
            if (POLICY_EXEMPTIONS.contains(relativePath)) {
                continue;
            }
            String source = Files.readString(sourceRoot().resolve(relativePath));
            if (!source.contains("DataScopePolicy")) {
                violations.add(relativePath);
            }
        }

        assertThat(violations)
                .as("Remaining direct data-scope consumers must also expose a user-domain DataScopePolicy path")
                .isEmpty();
    }

    private Set<String> scanDirectDataScopeConsumers() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        Path sourceRoot = sourceRoot();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !isExcluded(path))
                    .forEach(path -> {
                        try {
                            if (DIRECT_SCOPE_LOGIC.matcher(Files.readString(path)).find()) {
                                result.add(sourceRoot.relativize(path).toString().replace('\\', '/'));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }
        return result;
    }

    private boolean isExcluded(Path path) {
        String relative = sourceRoot().relativize(path).toString().replace('\\', '/');
        return relative.startsWith("domain/user/")
                || relative.equals("auth/service/SysUserService.java")
                || relative.equals("service/UserDomainService.java")
                || relative.startsWith("common/enums/")
                || relative.startsWith("testsupport/");
    }

    private Set<String> loadLegacyWhitelist() throws IOException {
        Set<String> lines = new LinkedHashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/ddd/data-scope-consumer-legacy-whitelist.txt")) {
            assertThat(in).as("data-scope consumer legacy whitelist resource").isNotNull();
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

    private static Path sourceRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("src/main/java/com/colonel/saas"))) {
            return cwd.resolve("src/main/java/com/colonel/saas");
        }
        return cwd.resolve("backend/src/main/java/com/colonel/saas");
    }
}
