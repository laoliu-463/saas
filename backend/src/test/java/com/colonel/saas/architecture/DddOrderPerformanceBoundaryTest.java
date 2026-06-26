package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderPerformanceBoundaryTest {

    @Test
    void orderController_shouldNotOwnPerformanceWriteOperations() throws IOException {
        String source = Files.readString(sourcePath(
                "src/main/java/com/colonel/saas/controller/OrderController.java"));

        assertThat(source)
                .doesNotContain("CommissionService")
                .doesNotContain("PerformanceBackfillService")
                .doesNotContain("performance-backfill")
                .doesNotContain("performance-reconcile-invalidated")
                .doesNotContain("commission-batch")
                .doesNotContain("commission-recalculate")
                .doesNotContain("performance_records");
    }

    @Test
    void performanceOpsController_shouldOwnCanonicalAndLegacyPerformanceOpsRoutes() throws IOException {
        String source = Files.readString(sourcePath(
                "src/main/java/com/colonel/saas/controller/PerformanceOpsController.java"));

        assertThat(source)
                .contains("@RequireRoles({RoleCodes.ADMIN})")
                .contains("\"/performance/backfill\"")
                .contains("\"/orders/performance-backfill\"")
                .contains("\"/performance/reconcile-invalidated\"")
                .contains("\"/orders/performance-reconcile-invalidated\"")
                .contains("\"/performance/commission-batch\"")
                .contains("\"/orders/commission-batch\"")
                .contains("\"/performance/commission-recalculate\"")
                .contains("\"/orders/commission-recalculate\"");
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
