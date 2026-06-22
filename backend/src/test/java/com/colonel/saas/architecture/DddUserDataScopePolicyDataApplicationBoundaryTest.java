package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDataApplicationBoundaryTest {

    @Test
    void dataApplication_shouldConsumeUserDomainDataScopePolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/data/DataApplicationService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopePolicy")
                .contains("applyQueryDataScopeLegacy")
                .contains("applyQueryDataScopeWithPolicy")
                .contains("applyLambdaDataScopeLegacy")
                .contains("applyLambdaDataScopeWithPolicy")
                .contains("requireDataScopeContextLegacy")
                .contains("requireDataScopeContextWithPolicy")
                .doesNotContain("switch (dataScope)");
    }
}
