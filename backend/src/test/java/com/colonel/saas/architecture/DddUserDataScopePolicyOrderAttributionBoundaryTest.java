package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyOrderAttributionBoundaryTest {

    @Test
    void orderSide_shouldGateDataScopeResolverPathBehindFeatureFlag() throws Exception {
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/controller/OrderController.java");
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/service/OrderService.java");
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/service/OrderAttributionService.java");
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java");
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/domain/order/infrastructure/OrderFilterOptionsMapperAdapter.java");
        assertUsesResolverWithoutPolicyImport("src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java");
    }

    @Test
    void orderAttributionService_shouldGateResolverPathBehindFeatureFlag() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/OrderAttributionService.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopeResolver")
                .contains("applyDataScopeLegacy")
                .contains("applyDataScopeWithPolicy")
                .contains("dataScopeResolver.applyTo")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;");
    }

    @Test
    void orderDetailQuery_shouldUseResolvedDataScopeInsteadOfPolicyEnums() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java"));

        assertThat(source)
                .contains("DataScopeResolver.ResolvedDataScope resolved")
                .contains("dataScopeResolver.resolve")
                .contains("resolved.contextSatisfied()")
                .contains("resolved.noFilter()")
                .contains("resolved.filtersUser()")
                .contains("resolved.filtersDept()")
                .doesNotContain("DataScopePolicy.Decision")
                .doesNotContain("DataScopePolicy.ContextRequirement");
    }

    private void assertUsesResolverWithoutPolicyImport(String relativePath) throws Exception {
        String source = Files.readString(Path.of(relativePath));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("dataScopeResolver")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("dataScopePolicy.");
    }
}
