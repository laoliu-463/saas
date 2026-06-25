package com.colonel.saas.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-SLIM-ORDER-001：OrderSyncService 不再直接引用 OrderAmountMapperPolicy。
 */
class DddSlimOrder001RoutingTest {

    @Test
    @DisplayName("OrderSyncService 金额映射委派 OrderAmountMappingRouter.mapAndApplyToOrder")
    void orderSyncServiceShouldDelegateAmountMappingToRouter() throws Exception {
        Path source = Path.of("src/main/java/com/colonel/saas/service/OrderSyncService.java");
        String content = Files.readString(source);

        assertThat(content).contains("orderAmountMappingRouter.mapAndApplyToOrder(");
        assertThat(content).doesNotContain("OrderAmountMapperPolicy.");
        assertThat(content).doesNotContain("OrderDualTrackAmountResolver.");
    }
}
