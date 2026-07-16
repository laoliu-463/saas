package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceExceptionAndDuplicateContractTest {

    @Test
    void calculationShouldStayOnSingleUpsertPathForDuplicateEvents() throws IOException {
        String application = readProjectFile(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java");
        String mapper = readProjectFile("src/main/resources/mapper/PerformanceRecordMapper.xml");
        String applicationTest = readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java");
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java");

        assertThat(application)
                .contains("order == null || !StringUtils.hasText(order.getOrderId())")
                .contains("PerformanceRecord existing = performanceRecordMapper.findByOrderId(order.getOrderId())")
                .contains("performanceRecordMapper.upsert(record)");
        assertThat(mapper)
                .contains("ON CONFLICT (order_id) DO UPDATE")
                .contains("calculation_version = performance_records.calculation_version + 1");
        assertThat(applicationTest)
                .contains("upsertFromOrder_existingRecordShouldReuseIdAndAdvanceVersionForDuplicateConsumption");
        assertThat(listenerTest)
                .contains("onOrderSynced_shouldDelegateDuplicateEventsToUpsertWithoutCreatingSeparatePath");
    }

    @Test
    void listenerExceptionBranchesShouldAvoidPublishingUnprovenPerformanceEvents() throws IOException {
        String listener = readProjectFile("src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java");
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java");

        assertThat(listener)
                .contains("try {")
                .contains("if (order == null)")
                .contains("if (executionService != null)")
                .contains("eventPublisher.publishEvent")
                .contains("catch (RuntimeException error)")
                .contains("executionService.markFailed(eventKey, error)")
                .contains("throw error;");
        assertThat(listenerTest)
                .contains("onOrderSynced_shouldPropagateMissingOrderSoOutboxCanRetry")
                .contains("onOrderSynced_shouldPropagateCalculationFailureSoOutboxCanRetry");
    }

    @Test
    void backfillExceptionBranchesShouldCountFailuresAndContinue() throws IOException {
        String backfill = readProjectFile("src/main/java/com/colonel/saas/service/PerformanceBackfillService.java");
        String backfillTest = readProjectFile("src/test/java/com/colonel/saas/service/PerformanceBackfillServiceTest.java");

        assertThat(backfill)
                .contains("failed++")
                .contains("errors.size() < 20")
                .contains("new BackfillResult(orders.size(), upserted, failed");
        assertThat(backfillTest)
                .contains("backfill_shouldCountFailureAndContinueWhenSingleOrderFails");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
