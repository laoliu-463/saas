package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDashboardBoundaryTest {

    @Test
    void dashboardService_shouldDelegateDataScopeDecisionToUserResolver() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/DashboardService.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopeResolver")
                .contains("appendScopeClauseLegacy")
                .contains("appendScopeClauseWithResolver")
                .contains("buildOrderVisibility")
                .contains("buildOrderVisibilityLegacy")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("switch (dataScope)");
    }
}
