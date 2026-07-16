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
                .contains("FROM performance_records pr")
                .contains("FROM performance_adjustment_ledger")
                .contains("pr.is_valid = TRUE")
                .contains("pr.is_reversed = FALSE");
        assertThat(test)
                .contains("FROM performance_records pr")
                .contains("FROM performance_adjustment_ledger")
                .contains("pr.is_valid = TRUE")
                .contains("pr.is_reversed = FALSE");
    }

    @Test
    void refundFactsShouldWriteIdempotentAdjustmentsAndRebuildDashboardFromPerformanceFacts() throws IOException {
        String source = mainSource("com/colonel/saas/service/DashboardPerformanceSummaryService.java");
        String refundService = mainSource("com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentService.java");
        String test = testSource("com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java");

        assertThat(source)
                .contains("public void applyPerformanceCalculated(PerformanceCalculatedEvent event)")
                .contains("FROM performance_records pr")
                .contains("FROM performance_adjustment_ledger");
        assertThat(refundService)
                .contains("String eventKey = eventKey(event)")
                .contains("ledgerMapper.selectOne")
                .contains("ledgerMapper.insert(ledger)")
                .contains("ledger.setDeltaEffectiveServiceProfit");
        assertThat(test)
                .contains("applyPerformanceCalculated_shouldRebuildDailySummaryFromPerformanceRecord")
                .contains("FROM performance_records");
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
