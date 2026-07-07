package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDataApplicationBoundaryTest {

    @Test
    void dataApplication_shouldConsumeUserDomainDataScopeResolver() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/data/DataApplicationService.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopeResolver")
                .contains("applyQueryDataScopeLegacy")
                .contains("applyQueryDataScopeWithResolver")
                .contains("applyLambdaDataScopeLegacy")
                .contains("applyLambdaDataScopeWithResolver")
                .contains("requireDataScopeContextLegacy")
                .contains("requireDataScopeContextWithResolver")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("switch (dataScope)");
    }

    @Test
    void dataController_shouldInjectDataScopeResolverInsteadOfPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/controller/DataController.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("dataScopeResolver")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("DataScopePolicy dataScopePolicy");
    }
}
