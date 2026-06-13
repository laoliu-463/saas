package com.colonel.saas.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-SLIM-PERF-001：CommissionService 不再内联双轨金额公式。
 */
class DddSlimPerf001RoutingTest {

    @Test
    @DisplayName("CommissionService 金额公式委派 PerformanceMoneyPolicy")
    void commissionServiceShouldDelegateMoneyFormulaToPolicy() throws Exception {
        Path source = Path.of("src/main/java/com/colonel/saas/service/CommissionService.java");
        String content = Files.readString(source);

        assertThat(content).contains("PerformanceMoneyPolicy.calculate(");
        assertThat(content).contains("PerformanceMoneyPolicy.serviceFeeNetCent(");
        assertThat(content).doesNotContain("private long multiplyCent(");
    }
}
