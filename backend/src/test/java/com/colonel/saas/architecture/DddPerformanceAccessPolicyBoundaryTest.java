package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceAccessPolicyBoundaryTest {

    @Test
    void performanceAccessPolicy_shouldLiveInDomainPolicyPackage() throws IOException {
        Path mainSource = sourcePath("src/main/java");

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
        assertThat(sourcePath("src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java"))
                .exists();
        assertThat(sourcePath("src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessContext.java"))
                .exists();
    }

    @Test
    void performanceAccessScope_shouldDelegateRoleCodeMatchingToUserPolicy() throws IOException {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java"));

        assertThat(source)
                .doesNotContain("private static boolean hasAnyRole")
                .doesNotContain("toLowerCase(Locale.ROOT)")
                .doesNotContain("CurrentUserPermissionPolicy")
                .doesNotContain("USER_PERMISSION_POLICY")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole");
    }

    @Test
    void performanceQueryService_shouldGateDataScopeResolverPathBehindFeatureFlag() throws IOException {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/service/PerformanceQueryService.java"));

        assertThat(source)
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("DataScopeResolver")
                .contains("CurrentUserPermissionChecker")
                .contains("PerformanceAccessScope.appendScopeConditionWithResolver")
                .contains("PerformanceAccessScope.appendScopeCondition")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy")
                .doesNotContain("new DataScopePolicy()");
    }

    @Test
    void performanceAccessScope_shouldOfferDataScopeResolverSidePath() throws IOException {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("dataScopeResolver.resolve")
                .contains("DataScopeResolver.ResolvedDataScope")
                .doesNotContain("DataScopePolicy")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("DataScopePolicy.Decision")
                .doesNotContain("DataScopePolicy.ContextRequirement");
    }

    @Test
    void performanceUserPolicyConsumers_shouldNotDependOnDirectUserPolicyClasses() throws IOException {
        String[] performanceConsumers = {
                "src/main/java/com/colonel/saas/controller/PerformanceController.java",
                "src/main/java/com/colonel/saas/service/PerformanceQueryService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java",
                "src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java"
        };

        for (String relativePath : performanceConsumers) {
            String source = Files.readString(sourcePath(relativePath));
            assertThat(source)
                    .as(relativePath)
                    .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy")
                    .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy")
                    .doesNotContain("dataScopePolicy.")
                    .doesNotContain("currentUserPermissionPolicy.")
                    .doesNotContain("new DataScopePolicy()")
                    .doesNotContain("new CurrentUserPermissionPolicy()");
        }
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
