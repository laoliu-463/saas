package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicySampleApplicationBoundaryTest {

    @Test
    void sampleApplication_shouldGateDataScopePolicyPathBehindFeatureFlag() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("shouldUseAuditorQueryLegacy")
                .contains("shouldUseAuditorQueryWithPolicy")
                .contains("dataScopePolicy.contextRequirement")
                .contains("dataScopePolicy.decide");
    }

    @Test
    void sampleQueryConfiguration_shouldInjectDataScopePolicyIntoApplicationDelegate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleQueryConfiguration.java"));

        assertThat(source)
                .contains("DataScopePolicy dataScopePolicy")
                .contains("DddRefactorProperties dddRefactorProperties")
                .contains("dataScopePolicy")
                .contains("dddRefactorProperties");
    }
}
