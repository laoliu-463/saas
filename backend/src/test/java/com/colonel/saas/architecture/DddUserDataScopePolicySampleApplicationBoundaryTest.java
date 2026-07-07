package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicySampleApplicationBoundaryTest {

    @Test
    void sampleApplication_shouldResolveDataScopeThroughUserResolverBehindFeatureFlag() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("shouldUseAuditorQueryLegacy")
                .contains("shouldUseAuditorQueryWithPolicy")
                .contains("shouldUseBoardAuditorQuery")
                .contains("shouldUseExportAuditorQuery")
                .contains("canAccessSampleByDataScopeLegacy")
                .contains("canAccessSampleByDataScopeWithPolicy")
                .contains("DataScopeResolver.ResolvedDataScope resolved")
                .contains("dataScopeResolver.resolve")
                .contains("resolved.missingUser()")
                .contains("resolved.contextSatisfied()")
                .contains("resolved.filtersUser()")
                .contains("resolved.filtersDept()")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.");
    }

    @Test
    void sampleQueryConfiguration_shouldInjectDataScopeResolverIntoApplicationDelegate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleQueryConfiguration.java"));

        assertThat(source)
                .contains("DataScopeResolver dataScopeResolver")
                .contains("DddRefactorProperties dddRefactorProperties")
                .contains("dataScopeResolver")
                .contains("dddRefactorProperties")
                .doesNotContain("DataScopePolicy dataScopePolicy")
                .doesNotContain("dataScopePolicy");
    }
}
