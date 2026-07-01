package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-PERF-001: calculation orchestration must enter through the performance application service.
 *
 * <p>Slice 5 更新：PerformanceMonthRecalculationService 已变为 thin shell 委派壳，
 * 真实调用链下沉至 PerformanceMonthRecalculationApplicationService，
 * 由 Application 层调 PerformanceCalculationApplicationService。本测试改检查：
 * <ul>
 *   <li>Service 不再直接调 PerformanceCalculationApplicationService（委派壳）</li>
 *   <li>Service 委派给 PerformanceMonthRecalculationApplicationService</li>
 *   <li>Application 层持有 PerformanceCalculationApplicationService 引用</li>
 * </ul></p>
 */
class DddPerf001CalculationApplicationServiceRoutingTest {

    private static final String APPLICATION_SERVICE =
            "com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService";
    private static final String MONTH_RECALC_APPLICATION =
            "com.colonel.saas.domain.performance.application.PerformanceMonthRecalculationApplicationService";
    private static final String LEGACY_SERVICE_IMPORT =
            "import com.colonel.saas.service.PerformanceCalculationService;";

    @Test
    void performanceCalculationApplicationService_shouldExist() throws ClassNotFoundException {
        assertThat(Class.forName(APPLICATION_SERVICE))
                .as("DDD-PERF-001 requires PerformanceCalculationApplicationService")
                .isNotNull();
    }

    @Test
    void performanceCalculationEntrypoints_shouldRouteThroughApplicationService() throws IOException {
        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        // 其他入口（listener / backfill service）仍直接 import PerformanceCalculationApplicationService
        List<Path> directEntrypoints = List.of(
                sourceRoot.resolve("com/colonel/saas/listener/PerformanceRecordSyncListener.java"),
                sourceRoot.resolve("com/colonel/saas/service/PerformanceBackfillService.java"));

        for (Path entrypoint : directEntrypoints) {
            String content = Files.readString(entrypoint);
            assertThat(content)
                    .as(entrypoint.getFileName() + " must import PerformanceCalculationApplicationService")
                    .contains("import " + APPLICATION_SERVICE + ";");
            assertThat(content)
                    .as(entrypoint.getFileName() + " must not inject legacy PerformanceCalculationService directly")
                    .doesNotContain(LEGACY_SERVICE_IMPORT);
        }

        // PerformanceMonthRecalculationService (Slice 5) 已变 thin shell 委派壳：
        // 不再直接 import PerformanceCalculationApplicationService，而是委派给
        // PerformanceMonthRecalculationApplicationService，后者持有 Application 调用链。
        Path monthRecalcService = sourceRoot.resolve(
                "com/colonel/saas/service/PerformanceMonthRecalculationService.java");
        String monthRecalcContent = Files.readString(monthRecalcService);
        assertThat(monthRecalcContent)
                .as("PerformanceMonthRecalculationService (Slice 5) must delegate to MonthRecalculation Application")
                .contains(MONTH_RECALC_APPLICATION)
                .doesNotContain("import " + APPLICATION_SERVICE + ";")
                .doesNotContain(LEGACY_SERVICE_IMPORT);

        // MonthRecalculation Application 必须持有 PerformanceCalculationApplicationService 引用
        // (同包 domain.performance.application 不需要 import, 直接用类名)
        Path monthRecalcApplication = sourceRoot.resolve(
                "com/colonel/saas/domain/performance/application/PerformanceMonthRecalculationApplicationService.java");
        String monthRecalcAppContent = Files.readString(monthRecalcApplication);
        assertThat(monthRecalcAppContent)
                .as("PerformanceMonthRecalculationApplicationService must reference PerformanceCalculationApplicationService")
                .contains("PerformanceCalculationApplicationService");
    }
}
