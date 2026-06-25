package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-CLEAN-001: keep order-owned code off direct user mappers/services.
 */
class DddClean001OrderUserDependencyGuardTest {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern TYPE_NAME =
            Pattern.compile("(?:public\\s+)?(?:class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern SYS_USER_SERVICE_TOKEN =
            Pattern.compile("\\bSysUserService\\b");

    @Test
    void orderOwnedClassesMustNotInjectSysUserMapper() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> violations = CrossDomainMapperScanner.scanMainSources(backendRoot).stream()
                .filter(injection -> "SysUserMapper".equals(injection.mapperSimpleName()))
                .filter(injection -> MapperDomainRegistry.ownerDomain(injection.ownerFqcn())
                        == MapperDomainRegistry.Domain.ORDER)
                .map(CrossDomainMapperScanner.Injection::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(violations)
                .as("DDD-CLEAN-001: order code must use UserDomainFacade, not SysUserMapper")
                .isEmpty();
    }

    @Test
    void orderOwnedClassesMustNotDependOnSysUserService() throws IOException {
        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Set<String> violations = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(backendRoot.resolve("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(path);
                String fqcn = readFqcn(content);
                if (fqcn == null || MapperDomainRegistry.ownerDomain(fqcn) != MapperDomainRegistry.Domain.ORDER) {
                    continue;
                }
                if (SYS_USER_SERVICE_TOKEN.matcher(content).find()) {
                    violations.add(fqcn);
                }
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-001: order code must use UserDomainFacade, not SysUserService")
                .isEmpty();
    }

    @Test
    void legacyWhitelistMustNotKeepOrderSysUserMapperDebt() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/ddd/cross-domain-mapper-legacy-whitelist.txt")) {
            assertThat(in).as("legacy whitelist resource").isNotNull();
            String text = new String(in.readAllBytes());
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.endsWith("|SysUserMapper")) {
                    continue;
                }
                String owner = trimmed.substring(0, trimmed.indexOf('|'));
                if (MapperDomainRegistry.ownerDomain(owner) == MapperDomainRegistry.Domain.ORDER) {
                    violations.add(trimmed);
                }
            }
        }

        assertThat(violations)
                .as("DDD-CLEAN-001: whitelist must not preserve order SysUserMapper exceptions")
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
}
