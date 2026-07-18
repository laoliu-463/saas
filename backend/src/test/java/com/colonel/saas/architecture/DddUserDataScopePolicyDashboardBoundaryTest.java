package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDashboardBoundaryTest {

    @Test
    void dashboardService_shouldDelegateDataScopeDecisionToUserResolver() throws Exception {
        String serviceSource = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/DashboardService.java"));
        String policySource = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/order/policy/DashboardOrderAccessPolicy.java"));

        assertThat(serviceSource)
                .contains("DataScopeResolver")
                .contains("DashboardOrderAccessPolicy")
                .contains("orderAccessPolicy.resolveOrderVisibility")
                .contains("appendScopeClauseLegacy")
                .contains("appendScopeClauseWithResolver")
                .contains("buildOrderVisibility")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("switch (dataScope)");

        assertThat(policySource)
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopeResolver.resolve")
                .contains("resolveLegacyVisibility")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("switch (dataScope)");
    }
}
