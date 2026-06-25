package com.colonel.saas.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-SLIM-ORDER-002：OrderSyncService 不再直接操作归因字段与 AttributionService 常量。
 */
class DddSlimOrder002RoutingTest {

    @Test
    @DisplayName("OrderSyncService 归因委派 OrderAttributionRouter")
    void orderSyncServiceShouldDelegateAttributionToRouter() throws Exception {
        Path source = Path.of("src/main/java/com/colonel/saas/service/OrderSyncService.java");
        String content = Files.readString(source);

        assertThat(content).contains("orderAttributionRouter.resolveAndApply(");
        assertThat(content).contains("orderAttributionRouter.applyInitialUnattributedStatus(");
        assertThat(content).doesNotContain("attributionService.resolveAttribution(");
        assertThat(content).doesNotContain("AttributionService.REASON_");
        assertThat(content).doesNotContain("setAttributionStatus(\"UNATTRIBUTED\")");
    }
}
