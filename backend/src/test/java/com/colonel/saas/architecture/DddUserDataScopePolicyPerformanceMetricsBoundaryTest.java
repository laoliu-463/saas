package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyPerformanceMetricsBoundaryTest {

    /**
     * Slice 3 更新：appendScopeLegacy / appendScopeWithResolver 已从
     * PerformanceMetricsQueryService 下沉至 PerformanceAggregateApplicationService，
     * 所以数据范围双路径边界检查的目标文件改为 Application 层。
     * Service 文件不应再有用户域数据范围调用链，改为验证其仅持有 Application 引用。
     */
    @Test
    void performanceAggregateApplicationService_shouldGateDataScopeResolverPathBehindFeatureFlag() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java"));

        assertThat(source)
                .contains("DataScopeResolver")
                .contains("DddRefactorProperties")
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("dataScopeResolver")
                .contains("appendScopeLegacy")
                .contains("appendScopeWithResolver")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("switch (dataScope)");
    }

    @Test
    void performanceMetricsQueryService_shouldNotOwnDataScopePolicyBranchAfterSlice3() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java"));

        // Slice 3 后 Service 是 thin shell 委派壳，不应再持有 appendScope* SQL 装配 helper。
        assertThat(source)
                .doesNotContain("appendScopeLegacy")
                .doesNotContain("appendScopeWithResolver")
                .doesNotContain("orderFactsPerformanceJoin")
                .doesNotContain("appendBusinessLineFilter")
                .doesNotContain("appendRangeFilter")
                .contains("PerformanceAggregateApplicationService")
                .contains("aggregateApplicationService.aggregateRange")
                .contains("aggregateApplicationService.trendByDay")
                .contains("aggregateApplicationService.aggregateDashboardSummary");
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
