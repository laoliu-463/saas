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
 */
class DddPerf001CalculationApplicationServiceRoutingTest {

    private static final String APPLICATION_SERVICE =
            "com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService";
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
        List<Path> entrypoints = List.of(
                sourceRoot.resolve("com/colonel/saas/listener/PerformanceRecordSyncListener.java"),
                sourceRoot.resolve("com/colonel/saas/service/PerformanceBackfillService.java"),
                sourceRoot.resolve("com/colonel/saas/service/PerformanceMonthRecalculationService.java"));

        for (Path entrypoint : entrypoints) {
            String content = Files.readString(entrypoint);
            assertThat(content)
                    .as(entrypoint.getFileName() + " must import PerformanceCalculationApplicationService")
                    .contains("import " + APPLICATION_SERVICE + ";");
            assertThat(content)
                    .as(entrypoint.getFileName() + " must not inject legacy PerformanceCalculationService directly")
                    .doesNotContain(LEGACY_SERVICE_IMPORT);
        }
    }
}
