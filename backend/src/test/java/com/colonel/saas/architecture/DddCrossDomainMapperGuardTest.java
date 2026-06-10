package com.colonel.saas.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-003: forbid new cross-domain MyBatis Mapper injections beyond the frozen legacy whitelist.
 */
class DddCrossDomainMapperGuardTest {

    @Test
    void crossDomainMapperInjectionsMustNotGrowBeyondLegacyWhitelist() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<CrossDomainMapperScanner.Injection> current =
                CrossDomainMapperScanner.crossDomainInjections(
                        CrossDomainMapperScanner.scanMainSources(backendRoot));
        Set<String> currentKeys = current.stream()
                .map(CrossDomainMapperScanner.Injection::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> legacy = loadLegacyWhitelist();
        Set<String> newViolations = new LinkedHashSet<>(currentKeys);
        newViolations.removeAll(legacy);

        assertThat(newViolations)
                .as("New cross-domain mapper injections detected. Use Facade/Port instead, "
                        + "or update harness/reports/ddd-dependency-map.md with ADR approval.")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustStillReflectDocumentedCrossDomainDebt() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> current = CrossDomainMapperScanner.crossDomainInjections(
                        CrossDomainMapperScanner.scanMainSources(backendRoot))
                .stream()
                .map(CrossDomainMapperScanner.Injection::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> legacy = loadLegacyWhitelist();
        Set<String> removed = new LinkedHashSet<>(legacy);
        removed.removeAll(current);

        assertThat(removed)
                .as("Legacy whitelist entries no longer present — update whitelist after Facade migration")
                .isEmpty();
    }

    @Test
    @Disabled("Maintenance only: run manually to refresh ddd/cross-domain-mapper-legacy-whitelist.txt")
    void dumpCrossDomainWhitelistCandidates() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        CrossDomainMapperScanner.crossDomainInjections(
                        CrossDomainMapperScanner.scanMainSources(backendRoot))
                .stream()
                .map(CrossDomainMapperScanner.Injection::key)
                .sorted()
                .forEach(System.out::println);
    }

    private Set<String> loadLegacyWhitelist() throws IOException {
        Set<String> lines = new LinkedHashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/ddd/cross-domain-mapper-legacy-whitelist.txt")) {
            assertThat(in).as("legacy whitelist resource").isNotNull();
            String text = new String(in.readAllBytes());
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
