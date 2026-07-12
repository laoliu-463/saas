package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceIdempotencyContractTest {

    @Test
    void performanceRecordsShouldUseOrderIdUpsertForDuplicateOrderConsumption() throws IOException {
        String mapperXml = readProjectFile("src/main/resources/mapper/PerformanceRecordMapper.xml");

        assertThat(mapperXml)
                .contains(
                        "<insert id=\"upsert\"",
                        "INSERT INTO performance_records",
                        "ON CONFLICT (order_id) DO UPDATE SET",
                        "calculation_version = performance_records.calculation_version + 1");
    }

    @Test
    void performanceCalculationShouldLookUpExistingRecordBeforeUpsert() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java");

        assertThat(service)
                .contains(
                        "performanceRecordMapper.findByOrderId(order.getOrderId())",
                        "buildRecord(order, existing)",
                        "performanceRecordMapper.upsert(record)");
    }

    @Test
    void orderSyncedDuplicateConsumptionShouldStayOnUpsertPath() throws IOException {
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java");
        String applicationTest = readProjectFile(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java");

        assertThat(listenerTest)
                .contains(
                        "onOrderSynced_shouldDelegateDuplicateEventsToUpsertWithoutCreatingSeparatePath",
                        "verify(performanceCalculationApplicationService, times(2)).upsertFromOrder(order)");
        assertThat(applicationTest)
                .contains(
                        "upsertFromOrder_existingRecordShouldReuseIdAndAdvanceVersionForDuplicateConsumption",
                        "assertThat(result.getId()).isEqualTo(existingId)",
                        "assertThat(result.getCalculationVersion()).isEqualTo(4)");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
