package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 超千行 Service 债务只能下降，且不得继续吸收 Mapper 依赖。 */
class LargeServiceDebtRedlineTest {

    private static final int LARGE_SERVICE_THRESHOLD = 1_000;
    private static final Pattern MAPPER_REFERENCE = Pattern.compile(
            "com\\.colonel\\.saas\\.mapper\\.([A-Za-z0-9_]+)");

    @Test
    void largeServiceLineCountsMustNotGrowAndNewLargeServicesAreForbidden() throws IOException {
        Map<String, Integer> baseline = loadLineBaseline();
        Map<String, Integer> current = scanLargeServices();

        assertThat(current.keySet())
                .as("No new Service may cross 1000 lines; split a small application slice first")
                .isSubsetOf(baseline.keySet());
        current.forEach((path, lines) -> assertThat(lines)
                .as(path + " may only shrink from its recorded debt baseline")
                .isLessThanOrEqualTo(baseline.get(path)));

        Set<String> retiredDebt = new LinkedHashSet<>(baseline.keySet());
        retiredDebt.removeAll(current.keySet());
        assertThat(retiredDebt)
                .as("Remove entries from large-service-line-baseline.csv after a Service drops to 1000 lines or less")
                .isEmpty();
    }

    @Test
    void largeServicesMustNotAddMapperDependencies() throws IOException {
        Set<String> baseline = resourceLines("/ddd/large-service-mapper-baseline.txt");
        Set<String> current = new LinkedHashSet<>();
        for (String service : loadLineBaseline().keySet()) {
            String source = Files.readString(backendRoot().resolve(service));
            Matcher matcher = MAPPER_REFERENCE.matcher(source);
            while (matcher.find()) {
                current.add(service + "|" + matcher.group(1));
            }
        }

        Set<String> additions = new LinkedHashSet<>(current);
        additions.removeAll(baseline);
        assertThat(additions)
                .as("Large Services must not add Mapper dependencies; use an application port/adapter")
                .isEmpty();

        Set<String> removed = new LinkedHashSet<>(baseline);
        removed.removeAll(current);
        assertThat(removed)
                .as("Remove retired Mapper debt from large-service-mapper-baseline.txt")
                .isEmpty();
    }

    private static Map<String, Integer> scanLargeServices() throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();
        Path root = backendRoot().resolve("src/main/java");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Service.java"))
                    .forEach(path -> {
                        try {
                            int lines = Files.readAllLines(path).size();
                            if (lines > LARGE_SERVICE_THRESHOLD) {
                                result.put(relativeBackendPath(path), lines);
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to read " + path, exception);
                        }
                    });
        }
        return result;
    }

    private static Map<String, Integer> loadLineBaseline() throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String line : resourceLines("/ddd/large-service-line-baseline.csv")) {
            String[] parts = line.split(",", 2);
            result.put(parts[0], Integer.parseInt(parts[1]));
        }
        return result;
    }

    private static Set<String> resourceLines(String resource) throws IOException {
        try (var stream = LargeServiceDebtRedlineTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            Set<String> result = new LinkedHashSet<>();
            for (String line : new String(stream.readAllBytes()).split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    result.add(trimmed);
                }
            }
            return result;
        }
    }

    private static String relativeBackendPath(Path path) {
        return backendRoot().relativize(path).toString().replace('\\', '/');
    }

    private static Path backendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("src/main/java")) ? cwd : cwd.resolve("backend");
    }
}
