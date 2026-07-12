package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderRefundReversalContractTest {

    @Test
    void orderReadFacadeShouldExposeOnlyInvalidatedFactsWithStalePerformance() throws IOException {
        String facade = readProjectFile("src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java");
        String legacyFacade = readProjectFile("src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java");
        String stalePerformanceQuery = section(
                legacyFacade,
                "public List<ColonelsettlementOrder> findInvalidatedOrdersWithStalePerformance(int limit)",
                "@Override\n    public List<ColonelsettlementOrder> findOrdersForBackfill(");

        assertThat(facade)
                .contains(
                        "List<ColonelsettlementOrder> findInvalidatedOrdersWithStalePerformance(int limit)",
                        "失效/退款订单上仍存在有效业绩记录的订单");

        assertThat(stalePerformanceQuery)
                .contains(
                        "OrderCommissionPolicy.STATUS_CANCELLED",
                        "OrderCommissionPolicy.STATUS_REFUNDED",
                        "EXISTS (",
                        "FROM performance_records pr",
                        "pr.order_id = colonelsettlement_order.order_id",
                        "pr.is_valid = TRUE",
                        "orderMapper.selectList");

        assertThat(stalePerformanceQuery.toLowerCase())
                .doesNotContain(
                        "insert into performance_records",
                        "update performance_records",
                        "delete from performance_records");
    }

    @Test
    void performanceBackfillShouldDelegateInvalidatedOrdersToPerformanceCalculationOnly() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/PerformanceBackfillService.java");
        String reconcile = section(
                source,
                "public BackfillResult reconcileInvalidatedPerformance(Integer limit)",
                "public BackfillResult backfill(");

        assertThat(reconcile)
                .contains(
                        "orderReadFacade.findInvalidatedOrdersWithStalePerformance(",
                        "normalizeLimit(limit)",
                        "performanceCalculationApplicationService.upsertFromOrder(order)",
                        "upserted++",
                        "failed++",
                        "return new BackfillResult(orders.size(), upserted, failed, false, errors);")
                .doesNotContain(
                        "PerformanceRecordMapper",
                        "ColonelsettlementOrderMapper",
                        "CommissionService");
    }

    @Test
    void performanceCalculationShouldReverseRefundedAndInvalidatedOrders() throws IOException {
        String policy = readProjectFile("src/main/java/com/colonel/saas/service/OrderCommissionPolicy.java");
        String application = readProjectFile(
                "src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java");
        String buildRecord = section(application, "private PerformanceRecord buildRecord(", "private void zeroCommissions(");
        String zeroCommissions = section(application, "private void zeroCommissions(PerformanceRecord record)", "private long nvl(");

        assertThat(policy)
                .contains(
                        "public static final int STATUS_CANCELLED = 4",
                        "public static final int STATUS_REFUNDED = 5",
                        "return orderStatus != STATUS_CANCELLED && orderStatus != STATUS_REFUNDED");

        assertThat(buildRecord)
                .contains(
                        "boolean reversed = !OrderCommissionPolicy.countsTowardPerformance(order.getOrderStatus());",
                        "record.setValid(!reversed);",
                        "record.setReversed(reversed);",
                        "record.setOrderStatus(order.getOrderStatus());",
                        "if (reversed) {\n            zeroCommissions(record);\n            return record;\n        }");

        assertThat(zeroCommissions)
                .contains(
                        "record.setEstimateServiceProfit(0L);",
                        "record.setEffectiveServiceProfit(0L);",
                        "record.setEstimateRecruiterCommission(0L);",
                        "record.setEffectiveRecruiterCommission(0L);",
                        "record.setEstimateChannelCommission(0L);",
                        "record.setEffectiveChannelCommission(0L);",
                        "record.setEstimateGrossProfit(0L);",
                    "record.setEffectiveGrossProfit(0L);");
    }

    @Test
    void refundFactSyncedEventShouldHavePublisherOutboxAndConsumers() throws IOException {
        String eventTypes = readProjectFile("src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java");
        String publisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java");
        String persistence = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java");
        String performanceListener = readProjectFile(
                "src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java");
        String analyticsConsumer = readProjectFile(
                "src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java");
        String analyticsListener = readProjectFile(
                "src/main/java/com/colonel/saas/listener/AnalyticsShadowEventListener.java");

        assertFileContains(
                "src/main/java/com/colonel/saas/domain/order/event/OrderRefundFactSyncedEvent.java",
                "record OrderRefundFactSyncedEvent",
                "String refundId",
                "Long refundAmount",
                "Integer previousStatus",
                "Integer status");
        assertThat(eventTypes)
                .contains("ORDER_REFUND_FACT_SYNCED", "OrderRefundFactSynced");
        assertThat(publisher)
                .contains(
                        "appendOrderRefundFactSyncedInTransaction",
                        "publishOrderRefundFactSynced",
                        "OrderDomainEventTypes.ORDER_REFUND_FACT_SYNCED",
                        "objectMapper.readValue(");
        assertThat(persistence)
                .contains(
                        "OrderCommissionPolicy.isInvalidatedStatus(order.getOrderStatus())",
                        "toOrderRefundFactSyncedEvent(",
                        "publishOrderRefundFactSynced(refundEvent)");
        assertThat(performanceListener)
                .contains(
                        "onOrderRefundFactSynced",
                        "OrderRefundFactSyncedEvent",
                        "recalculate(event.orderId())");
        assertThat(analyticsConsumer)
                .contains(
                        "resolveEventId(OrderRefundFactSyncedEvent event)",
                        "eventTypeFor(OrderRefundFactSyncedEvent ignored)",
                        "AnalyticsEventTypes.ORDER_REFUND_FACT_SYNCED");
        assertThat(analyticsListener)
                .contains(
                        "onOrderRefundFactSynced",
                        "AnalyticsEventConsumer.resolveEventId(event)",
                        "AnalyticsEventConsumer.eventTypeFor(event)");
    }

    @Test
    void existingBehaviorTestsShouldKeepRefundReversalDiscoverable() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderCommissionPolicyTest.java",
                "countsTowardCommission_shouldExcludeCancelledAndRefunded",
                "OrderCommissionPolicy.STATUS_CANCELLED",
                "OrderCommissionPolicy.STATUS_REFUNDED");
        assertFileContains(
                "src/test/java/com/colonel/saas/service/PerformanceCalculationServiceTest.java",
                "upsertFromOrder_shouldReverseCancelledOrders",
                "upsertFromOrder_shouldReverseRefundedOrders",
                "assertThat(saved.getReversed()).isTrue()",
                "assertThat(saved.getValid()).isFalse()");
        assertFileContains(
                "src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java",
                "upsertFromOrder_shouldZeroCommissionsForCancelledOrder",
                "assertThat(result.getReversed()).isTrue()",
                "assertThat(result.getValid()).isFalse()");
        assertFileContains(
                "src/test/java/com/colonel/saas/service/PerformanceBackfillServiceTest.java",
                "reconcileInvalidatedPerformance_shouldUpsertStaleInvalidatedOrders",
                "findInvalidatedOrdersWithStalePerformance(50)",
                "verify(performanceCalculationApplicationService).upsertFromOrder(order)");
        assertFileContains(
                "src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java",
                "onOrderRefundFactSynced_shouldUpsertRefundedOrderAndPublishReversalEvent");
        assertFileContains(
                "src/test/java/com/colonel/saas/listener/AnalyticsShadowEventListenerTest.java",
                "onOrderRefundFactSynced_shouldDelegateWithNamespacedEventIdAndRefundFactType");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);
        assertThat(source).as(relativePath).contains(expectedFragments);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("section start: %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("section end: %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex).replace("\r\n", "\n");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
