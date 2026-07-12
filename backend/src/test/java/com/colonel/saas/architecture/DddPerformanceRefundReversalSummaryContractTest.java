package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceRefundReversalSummaryContractTest {

    @Test
    void calculationApplicationShouldReverseRefundedExistingRecords() throws IOException {
        String source = testSource("com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java");

        assertThat(source)
                .contains("upsertFromOrder_shouldReverseRefundedExistingRecordAndAdvanceVersion")
                .contains("setOrderStatus(5)")
                .contains("getCalculationVersion()).isEqualTo(8)")
                .contains("getReversed()).isTrue()");
    }

    @Test
    void summaryApplicationShouldExcludeRefundedInvalidAndReversedRows() throws IOException {
        String source = mainSource("com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java");
        String test = testSource("com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java");

        assertThat(source)
                .contains("OrderCommissionPolicy.STATUS_CANCELLED")
                .contains("OrderCommissionPolicy.STATUS_REFUNDED")
                .contains("COALESCE(pr.is_valid, true) = true")
                .contains("COALESCE(pr.is_reversed, false) = false");
        assertThat(test)
                .contains("co.order_status IS NULL OR co.order_status NOT IN (4, 5)")
                .contains("COALESCE(pr.is_valid, true) = true")
                .contains("COALESCE(pr.is_reversed, false) = false");
    }

    @Test
    void dashboardDailySummaryShouldSkipRefundedOrders() throws IOException {
        String source = mainSource("com/colonel/saas/service/DashboardPerformanceSummaryService.java");
        String test = testSource("com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java");

        assertThat(source)
                .contains("OrderCommissionPolicy.countsTowardPerformance(event.orderStatus())");
        assertThat(test)
                .contains("applyOrderSynced_shouldSkipRefundedOrders")
                .contains("verifyNoInteractions(jdbcTemplate, eventPublisher)");
    }

    private static String mainSource(String relativePath) throws IOException {
        return Files.readString(sourceRoot().resolve(relativePath));
    }

    private static String testSource(String relativePath) throws IOException {
        return Files.readString(testRoot().resolve(relativePath));
    }

    private static Path sourceRoot() {
        return Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
    }

    private static Path testRoot() {
        return Paths.get(System.getProperty("user.dir")).resolve("src/test/java");
    }
}
