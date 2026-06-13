package com.colonel.saas.architecture;

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
 * DDD-CLEAN-003: keep performance-owned code off direct order/product/talent/user/config mappers.
 */
class DddClean003PerformanceCrossDomainMapperGuardTest {

    private static final Set<MapperDomainRegistry.Domain> FORBIDDEN_PERFORMANCE_DEPENDENCIES = Set.of(
            MapperDomainRegistry.Domain.ORDER,
            MapperDomainRegistry.Domain.PRODUCT,
            MapperDomainRegistry.Domain.TALENT,
            MapperDomainRegistry.Domain.USER,
            MapperDomainRegistry.Domain.CONFIG
    );

    @Test
    void performanceOwnedClassesMustNotInjectCrossDomainMappers() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> violations = CrossDomainMapperScanner.scanMainSources(backendRoot).stream()
                .filter(injection -> MapperDomainRegistry.ownerDomain(injection.ownerFqcn())
                        == MapperDomainRegistry.Domain.PERFORMANCE)
                .filter(injection -> FORBIDDEN_PERFORMANCE_DEPENDENCIES.contains(
                        MapperDomainRegistry.mapperDomain(injection.mapperSimpleName())))
                .map(CrossDomainMapperScanner.Injection::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(violations)
                .as("DDD-CLEAN-003: performance code must use domain Facade/Port boundaries, not cross-domain Mappers")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustNotKeepPerformanceCrossDomainMapperDebt() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/ddd/cross-domain-mapper-legacy-whitelist.txt")) {
            assertThat(in).as("legacy whitelist resource").isNotNull();
            String text = new String(in.readAllBytes());
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String owner = trimmed.substring(0, trimmed.indexOf('|'));
                String mapper = trimmed.substring(trimmed.indexOf('|') + 1);
                if (MapperDomainRegistry.ownerDomain(owner) == MapperDomainRegistry.Domain.PERFORMANCE
                        && FORBIDDEN_PERFORMANCE_DEPENDENCIES.contains(MapperDomainRegistry.mapperDomain(mapper))) {
                    violations.add(trimmed);
                }
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-003: whitelist must not preserve performance cross-domain mapper exceptions")
                .isEmpty();
    }
}
