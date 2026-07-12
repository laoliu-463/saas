package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderSyncedEventTimingContractTest {

    @Test
    void orderPersistenceShouldPublishOrderSyncedOnlyAfterDurableWriteAndFollowUps() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java");
        String persistOrder = section(
                source,
                "public boolean persistOrder(ColonelsettlementOrder order)",
                "private void mergeBySource(ColonelsettlementOrder existing, ColonelsettlementOrder incoming)");

        assertThat(source).contains("@Transactional(rollbackFor = Exception.class)");
        assertThat(persistOrder)
                .contains(
                        "int claimEffect = orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId());",
                        "OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));",
                        "int effect = orderMapper.insertIgnoreByOrderId(order);",
                        "if (claimEffect <= 0) {\n            return false;\n        }");

        assertThat(countOccurrences(
                persistOrder,
                "runAttributionFollowUps(order);\n            publishOrderSynced(order, false, previousStatus);"))
                .isEqualTo(2);
        assertThat(persistOrder)
                .contains("runAttributionFollowUps(order);\n        publishOrderSynced(order, true, null);");
    }

    @Test
    void orderSyncedPayloadShouldBeMappedAndPublishedByOrderDomainPublisherOnly() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java");
        String publishOrderSynced = section(
                source,
                "private void publishOrderSynced(ColonelsettlementOrder order, boolean newlyInserted, Integer previousStatus)",
                "private void runAttributionFollowUps(ColonelsettlementOrder order)");

        assertThat(publishOrderSynced)
                .contains(
                        "OrderSyncedEvent event = orderEventPayloadMapper.toOrderSyncedEvent(order, newlyInserted);",
                        "orderDomainEventPublisher.publishOrderSynced(event);",
                        "orderEventPayloadMapper.toOrderStatusChangedEvent(",
                        "orderDomainEventPublisher.publishOrderStatusChangedDirect(statusEvent);")
                .doesNotContain("applicationEventPublisher.publishEvent");

        assertFileContains(
                "src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java",
                "public OrderSyncedEvent toOrderSyncedEvent(ColonelsettlementOrder order, boolean newlyInserted)",
                "order.getOrderId()",
                "order.getPickSource()",
                "!newlyInserted",
                "LocalDateTime.now()");
    }

    @Test
    void springEventPublishShouldBeDeferredUntilCommitWhenTransactionSynchronizationIsActive() throws IOException {
        String source = readProjectFile(
                "src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java");
        String directPublish = section(
                source,
                "public void publishOrderSyncedDirect(OrderSyncedEvent event)",
                "public void publishOrderStatusChangedDirect(OrderStatusChangedEvent event)");

        assertThat(directPublish)
                .contains(
                        "if (!TransactionSynchronizationManager.isSynchronizationActive()) {",
                        "applicationEventPublisher.publishEvent(event);",
                        "TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {",
                        "public void afterCommit() {",
                        "applicationEventPublisher.publishEvent(event);");
    }

    @Test
    void outboxRoutingShouldAppendIdempotentOrderSyncedRecordInsteadOfSpringEvent() throws IOException {
        String source = readProjectFile(
                "src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java");
        String publishOrderSynced = section(
                source,
                "public void publishOrderSynced(OrderSyncedEvent event)",
                "public void publishOrderSyncedDirect(OrderSyncedEvent event)");

        assertThat(publishOrderSynced)
                .contains(
                        "if (isOutboxRoutingEnabled()) {",
                        "String eventKey = \"OrderSynced:\" + event.orderId() + \":\" + event.orderRowId();",
                        "appendOrderSyncedInTransaction(eventKey, event);",
                        "return;",
                        "publishOrderSyncedDirect(event);");

        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java",
                "appendOrderSynced_shouldWriteOutboxWhenRoutingEnabled",
                "verify(outboxEventAppender).appendIfAbsent(",
                "eq(OrderDomainEventTypes.ORDER_SYNCED)",
                "verify(applicationEventPublisher, never()).publishEvent(any())");
    }

    @Test
    void existingBehaviorTestsShouldKeepOrderSyncedTimingDiscoverable() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java",
                "persistOrder_shouldPublishOrderSyncedEventImmediatelyWhenNoTransactionSynchronizationActive",
                "persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit",
                "verifyNoInteractions(eventPublisher)",
                "synchronizations.forEach(TransactionSynchronization::afterCommit)",
                "persistOrder_shouldAppendOutboxInsteadOfSpringEventWhenRoutingEnabled",
                "verify(eventPublisher, never()).publishEvent(any())");
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

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
