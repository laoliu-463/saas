package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddConfigDomainParameterOnlyBoundaryTest {

    private static final Set<String> CONFIG_FACT_OWNER_FILES = Set.of(
            "com/colonel/saas/controller/RuleCenterController.java",
            "com/colonel/saas/controller/SysConfigController.java",
            "com/colonel/saas/domain/config/facade/LegacyConfigSeedFacade.java",
            "com/colonel/saas/entity/SystemConfig.java",
            "com/colonel/saas/entity/SystemConfigChangeLog.java",
            "com/colonel/saas/mapper/SystemConfigMapper.java",
            "com/colonel/saas/mapper/SystemConfigChangeLogMapper.java",
            "com/colonel/saas/service/BusinessRuleConfigService.java",
            "com/colonel/saas/service/RuleCenterService.java",
            "com/colonel/saas/service/SysConfigService.java"
    );

    private static final Set<String> EXPLICIT_CONFIG_BOUNDARY_FILES = Set.of(
            "com/colonel/saas/config/ConfigChangedEventFactory.java",
            "com/colonel/saas/config/ConfigConsumerDomain.java",
            "com/colonel/saas/config/ConfigDefinitionRegistry.java",
            "com/colonel/saas/config/RuleCenterSchemaRegistry.java",
            "com/colonel/saas/config/SystemConfigKeys.java",
            "com/colonel/saas/controller/CommissionRuleController.java",
            "com/colonel/saas/controller/RuleCenterController.java",
            "com/colonel/saas/controller/SysConfigController.java",
            "com/colonel/saas/service/BusinessRuleConfigService.java",
            "com/colonel/saas/service/CommissionRuleService.java",
            "com/colonel/saas/service/RuleCenterService.java",
            "com/colonel/saas/service/SysConfigService.java"
    );

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.colonel.saas.domain.analytics.",
            "com.colonel.saas.domain.colonel.",
            "com.colonel.saas.domain.order.",
            "com.colonel.saas.domain.performance.",
            "com.colonel.saas.domain.product.",
            "com.colonel.saas.domain.sample.",
            "com.colonel.saas.domain.talent.",
            "com.colonel.saas.douyin.",
            "com.colonel.saas.gateway.",
            "com.colonel.saas.job.",
            "com.colonel.saas.listener."
    );

    private static final Set<String> FORBIDDEN_SERVICE_IMPORTS = Set.of(
            "com.colonel.saas.service.DashboardService",
            "com.colonel.saas.service.ExclusiveMerchantService",
            "com.colonel.saas.service.OrderAttributionService",
            "com.colonel.saas.service.OrderService",
            "com.colonel.saas.service.PerformanceCalculationService",
            "com.colonel.saas.service.PerformanceQueryService",
            "com.colonel.saas.service.ProductQuickSampleService",
            "com.colonel.saas.service.ProductService",
            "com.colonel.saas.service.SampleEligibilityService",
            "com.colonel.saas.service.SampleLifecycleService",
            "com.colonel.saas.service.TalentQueryService",
            "com.colonel.saas.service.TalentService"
    );

    private static final Set<String> BUSINESS_ACTION_NAMES = Set.of(
            "apply",
            "approve",
            "backfill",
            "calculate",
            "claim",
            "complete",
            "copyPromotion",
            "recalculate",
            "reject",
            "ship",
            "sync",
            "upsertFromOrder"
    );

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern CONFIG_FACT_WRITE_IMPORT =
            Pattern.compile("^import\\s+(com\\.colonel\\.saas\\.(?:mapper\\.SystemConfig(?:ChangeLog)?Mapper|service\\.SysConfigService|entity\\.SystemConfig(?:ChangeLog)?))\\s*;", Pattern.MULTILINE);
    private static final Pattern CONFIG_FACT_WRITE_CALL =
            Pattern.compile("\\b(?:sysConfigService|systemConfigMapper|systemConfigChangeLogMapper)\\s*\\.\\s*(?:create|update|delete|batchUpdateByKeys|insert|updateById|deleteById|softDeleteById)\\s*\\(");
    private static final Pattern BUSINESS_ACTION_CALL =
            Pattern.compile("\\.(" + String.join("|", BUSINESS_ACTION_NAMES) + ")\\s*\\(");

    @Test
    void configBoundaryReview_shouldCoverCurrentConfigSources() throws IOException {
        assertThat(configBoundaryFiles())
                .contains(
                        "com/colonel/saas/domain/config/facade/ConfigDomainFacade.java",
                        "com/colonel/saas/domain/config/facade/LegacyConfigDomainFacade.java",
                        "com/colonel/saas/domain/config/facade/LegacyConfigSeedFacade.java",
                        "com/colonel/saas/service/BusinessRuleConfigService.java",
                        "com/colonel/saas/service/SysConfigService.java",
                        "com/colonel/saas/service/RuleCenterService.java",
                        "com/colonel/saas/config/ConfigDefinitionRegistry.java",
                        "com/colonel/saas/config/RuleCenterSchemaRegistry.java");
    }

    @Test
    void configBoundarySources_shouldNotImportBusinessCommandGatewaysOrConsumers() throws IOException {
        List<String> violations = new ArrayList<>();
        Path sourceRoot = sourceRoot();
        for (String relativePath : configBoundaryFiles()) {
            String source = Files.readString(sourceRoot.resolve(relativePath));
            Matcher matcher = IMPORT_PATTERN.matcher(source);
            while (matcher.find()) {
                String importedType = matcher.group(1);
                if (isForbiddenImport(importedType)) {
                    violations.add(relativePath + " imports " + importedType);
                }
            }
        }

        assertThat(violations)
                .as("config domain must expose parameters/events only and must not depend on business command flows")
                .isEmpty();
    }

    @Test
    void businessSources_shouldNotWriteConfigFactsDirectly() throws IOException {
        List<String> violations = new ArrayList<>();
        Path sourceRoot = sourceRoot();
        for (String relativePath : productionJavaFiles()) {
            if (isConfigFactOwnerFile(relativePath)) {
                continue;
            }
            String source = Files.readString(sourceRoot.resolve(relativePath));

            Matcher importMatcher = CONFIG_FACT_WRITE_IMPORT.matcher(source);
            while (importMatcher.find()) {
                violations.add(relativePath + " imports config fact writer " + importMatcher.group(1));
            }

            Matcher writeCallMatcher = CONFIG_FACT_WRITE_CALL.matcher(source);
            while (writeCallMatcher.find()) {
                violations.add(relativePath + " calls config fact writer " + writeCallMatcher.group());
            }
        }

        assertThat(violations)
                .as("business domains must use ConfigDomainFacade/query APIs and must not write system_config facts directly")
                .isEmpty();
    }

    @Test
    void businessActionScan_shouldCoverKnownDomainCommandVocabulary() {
        assertThat(BUSINESS_ACTION_NAMES)
                .contains(
                        "sync",
                        "backfill",
                        "calculate",
                        "recalculate",
                        "claim",
                        "apply",
                        "approve",
                        "reject",
                        "ship",
                        "complete",
                        "copyPromotion",
                        "upsertFromOrder");
    }

    @Test
    void configBoundarySources_shouldNotInvokeBusinessActions() throws IOException {
        List<String> violations = new ArrayList<>();
        Path sourceRoot = sourceRoot();
        for (String relativePath : configBoundaryFiles()) {
            String source = Files.readString(sourceRoot.resolve(relativePath));
            Matcher matcher = BUSINESS_ACTION_CALL.matcher(source);
            while (matcher.find()) {
                violations.add(relativePath + " calls business action ." + matcher.group(1) + "(...)");
            }
        }

        assertThat(violations)
                .as("config domain may record config changes and publish config events, but must not execute business actions")
                .isEmpty();
    }

    private static boolean isForbiddenImport(String importedType) {
        return FORBIDDEN_SERVICE_IMPORTS.contains(importedType)
                || FORBIDDEN_IMPORT_PREFIXES.stream().anyMatch(importedType::startsWith);
    }

    private static boolean isConfigFactOwnerFile(String relativePath) {
        return relativePath.startsWith("com/colonel/saas/config/")
                || relativePath.startsWith("com/colonel/saas/domain/config/")
                || CONFIG_FACT_OWNER_FILES.contains(relativePath);
    }

    private static Set<String> configBoundaryFiles() throws IOException {
        Path sourceRoot = sourceRoot();
        Set<String> files = new TreeSet<>(EXPLICIT_CONFIG_BOUNDARY_FILES);
        Path configDomainRoot = sourceRoot.resolve("com/colonel/saas/domain/config");
        try (Stream<Path> paths = Files.walk(configDomainRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> toUnixPath(sourceRoot.relativize(path)))
                    .forEach(files::add);
        }
        return files;
    }

    private static Set<String> productionJavaFiles() throws IOException {
        Path sourceRoot = sourceRoot();
        Set<String> files = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> toUnixPath(sourceRoot.relativize(path)))
                    .forEach(files::add);
        }
        return files;
    }

    private static Path sourceRoot() {
        return Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
