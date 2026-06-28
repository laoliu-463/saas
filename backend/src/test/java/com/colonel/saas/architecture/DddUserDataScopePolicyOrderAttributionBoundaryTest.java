package com.colonel.saas.architecture;

import com.colonel.saas.domain.order.application.OrderAttributionService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyOrderAttributionBoundaryTest {

    @Test
    void orderAttributionService_shouldGateDataScopePolicyPathBehindFeatureFlag() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/OrderAttributionService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopePolicy")
                .contains("applyDataScopeLegacy")
                .contains("applyDataScopeWithPolicy")
                .contains("dataScopePolicy.applyTo");
    }
}
