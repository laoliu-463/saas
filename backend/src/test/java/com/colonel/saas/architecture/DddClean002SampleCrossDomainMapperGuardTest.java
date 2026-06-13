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
 * DDD-CLEAN-002: keep sample-owned code off direct product/talent/user/config mappers.
 */
class DddClean002SampleCrossDomainMapperGuardTest {

    private static final Set<MapperDomainRegistry.Domain> FORBIDDEN_SAMPLE_DEPENDENCIES = Set.of(
            MapperDomainRegistry.Domain.PRODUCT,
            MapperDomainRegistry.Domain.TALENT,
            MapperDomainRegistry.Domain.USER,
            MapperDomainRegistry.Domain.CONFIG
    );

    @Test
    void sampleOwnedClassesMustNotInjectProductTalentUserOrConfigMappers() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> violations = CrossDomainMapperScanner.scanMainSources(backendRoot).stream()
                .filter(injection -> MapperDomainRegistry.ownerDomain(injection.ownerFqcn())
                        == MapperDomainRegistry.Domain.SAMPLE)
                .filter(injection -> FORBIDDEN_SAMPLE_DEPENDENCIES.contains(
                        MapperDomainRegistry.mapperDomain(injection.mapperSimpleName())))
                .map(CrossDomainMapperScanner.Injection::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(violations)
                .as("DDD-CLEAN-002: sample code must use domain Facade/Port boundaries, not cross-domain Mappers")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustNotKeepSampleProductTalentUserOrConfigMapperDebt() throws IOException {
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
                if (MapperDomainRegistry.ownerDomain(owner) == MapperDomainRegistry.Domain.SAMPLE
                        && FORBIDDEN_SAMPLE_DEPENDENCIES.contains(MapperDomainRegistry.mapperDomain(mapper))) {
                    violations.add(trimmed);
                }
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-002: whitelist must not preserve sample cross-domain mapper exceptions")
                .isEmpty();
    }
}
