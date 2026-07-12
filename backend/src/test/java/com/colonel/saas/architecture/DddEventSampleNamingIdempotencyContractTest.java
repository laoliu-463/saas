package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddEventSampleNamingIdempotencyContractTest {

    @Test
    void sampleLifecycleEventsShouldHaveStableNamesPayloadsAndIdempotentOutboxKeys() throws IOException {
        String eventTypes = readProjectFile("src/main/java/com/colonel/saas/constant/SampleDomainEventTypes.java");
        String publisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/sample/event/SampleDomainEventPublisher.java");

        assertThat(eventTypes)
                .contains(
                        "public static final String SAMPLE_CREATED = \"SampleCreated\"",
                        "public static final String SAMPLE_APPROVED = \"SampleApproved\"",
                        "public static final String SAMPLE_REJECTED = \"SampleRejected\"",
                        "public static final String SAMPLE_SHIPPED = \"SampleShipped\"",
                        "public static final String SAMPLE_SIGNED = \"SampleSigned\"",
                        "public static final String SAMPLE_COMPLETED = \"SampleCompleted\"",
                        "public static final String SAMPLE_CLOSED = \"SampleClosed\"");

        assertThat(publisher)
                .contains(
                        "private static final int EVENT_VERSION = 1;",
                        "publishSampleCreated(",
                        "publishSampleApproved(",
                        "publishSampleRejected(",
                        "publishSampleShipped(",
                        "publishSampleSigned(",
                        "publishSampleCompleted(",
                        "publishSampleClosed(",
                        "\"sampleRequestId\"",
                        "\"productId\"",
                        "\"talentId\"",
                        "\"channelId\"",
                        "\"trackingNo\"",
                        "\"orderId\"",
                        "\"completedAt\"",
                        "\"SampleCreated:\" + sample.getId()",
                        "\"SampleShipped:\" + sample.getId() + \":\" + trackingNo",
                        "\"SampleSigned:\" + sample.getId() + \":\"",
                        "\"SampleCompleted:\" + sample.getId() + \":\" + orderKey",
                        "OutboxEventAppender.AGGREGATE_SAMPLE",
                        "EVENT_VERSION",
                        "outboxEventAppender.appendIfAbsent(",
                        "publishSpring(event)");
    }

    @Test
    void sampleEventsShouldHaveOutboxConsumptionAndDispatcherEvidence() throws IOException {
        String router = readProjectFile(
                "src/main/java/com/colonel/saas/domain/sample/event/SampleDomainEventOutboxRouter.java");
        String dispatcherTest = readProjectFile("src/test/java/com/colonel/saas/job/DomainEventDispatcherJobTest.java");
        String publisherTest = readProjectFile(
                "src/test/java/com/colonel/saas/domain/sample/event/SampleDomainEventPublisherTest.java");
        String stateMachineContract = readProjectFile(
                "src/test/java/com/colonel/saas/architecture/DddSampleStateMachineIntegrationClosureContractTest.java");
        String orderConsumptionContract = readProjectFile(
                "src/test/java/com/colonel/saas/architecture/DddSampleOrderEventConsumptionClosureContractTest.java");

        assertThat(router)
                .contains(
                        "public boolean supports(String eventType)",
                        "eventType.startsWith(\"Sample\")",
                        "public void dispatch(DomainEventOutbox event)",
                        "sampleDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload())");

        assertThat(dispatcherTest)
                .contains(
                        "dispatchPendingEvents_sampleEvent_routesToSampleRouter",
                        "buildOutbox(\"SampleCreated\"",
                        "when(sampleDomainEventOutboxRouter.supports(\"SampleCreated\")).thenReturn(true)",
                        "verify(sampleDomainEventOutboxRouter).dispatch(event)",
                        "verify(domainEventOutboxService).markPublished(event.getEventId(), 0)",
                        "dispatchPendingEvents_firstFailure_retryCountStartsAtOne",
                        "when(sampleDomainEventOutboxRouter.supports(\"SampleApproved\")).thenReturn(true)",
                        "verify(domainEventOutboxService).markFailed(event.getEventId(), 1, \"oops\", 3)",
                        "dispatchPendingEvents_multipleEvents_processesInOrder",
                        "buildOutbox(\"SampleShipped\"");

        assertThat(publisherTest)
                .contains(
                        "publishSampleCreated_shouldAppendOutboxAndPublishSpringEvent",
                        "publishSampleCompleted_shouldUseIdempotentEventKey",
                        "verify(applicationEventPublisher).publishEvent(any(SampleCreatedEvent.class))",
                        "assertThat(keyCaptor.getValue()).isEqualTo(\"SampleCompleted:\" + id + \":ORDER-1\")");

        assertThat(stateMachineContract)
                .contains(
                        "sampleDomainEventPublisher.publishSampleShipped",
                        "sampleDomainEventPublisher.publishSampleSigned",
                        "sampleDomainEventPublisher.publishSampleCompleted");
        assertThat(orderConsumptionContract)
                .contains("sampleDomainEventPublisher.publishSampleCompleted");
    }

    @Test
    void productAndSharedEventsShouldKeepCurrentNamingPayloadAndIdempotencyRulesDiscoverable() throws IOException {
        String productTypes = readProjectFile("src/main/java/com/colonel/saas/constant/ProductDomainEventTypes.java");
        String productPublisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java");
        String productRouter = readProjectFile(
                "src/main/java/com/colonel/saas/domain/event/ProductDomainEventOutboxRouter.java");
        String productTest = readProjectFile(
                "src/test/java/com/colonel/saas/service/ProductDomainEventPublisherTest.java");
        String appender = readProjectFile("src/main/java/com/colonel/saas/domain/event/OutboxEventAppender.java");

        assertThat(productTypes)
                .contains(
                        "PRODUCT_LISTED = \"ProductListedEvent\"",
                        "PRODUCT_HIDDEN = \"ProductHiddenEvent\"",
                        "PRODUCT_OWNER_CHANGED = \"ProductOwnerChangedEvent\"",
                        "ACTIVITY_SYNC_COMPLETED = \"ActivitySyncCompletedEvent\"",
                        "PARTNER_SYNC_COMPLETED = \"PartnerSyncCompletedEvent\"",
                        "ACTIVITY_EXTENDED = \"ActivityExtendedEvent\"",
                        "PRODUCT_DISPLAY_RULE_APPLIED = \"ProductDisplayRuleAppliedEvent\"",
                        "PRODUCT_FORCE_DISPLAY_CHANGED = \"ProductForceDisplayChangedEvent\"",
                        "COLONEL_PARTNER_SYNCED = \"ColonelPartnerSyncedEvent\"");

        assertThat(productPublisher)
                .contains(
                        "private static final int EVENT_VERSION = 1;",
                        "\"ProductListed:\" + operationStateId + \":\" + displayRuleVersion",
                        "\"ProductHidden:\" + operationStateId + \":\" + reason",
                        "\"ProductOwnerChanged:\" + productId + \":\" + newAssigneeId",
                        "\"ActivitySyncCompleted:\" + activityId + \":\" + occurredAt.toLocalDate()",
                        "\"PartnerSyncCompleted:\" + partnerId + \":\" + occurredAt.toLocalDate()",
                        "\"ActivityExtended:\" + activityId + \":\" + newEndTime",
                        "\"ProductDisplayRuleApplied:\" + productId + \":\" + newRelationId + \":\" + ruleVersion",
                        "\"ProductForceDisplayChanged:\" + relationId + \":\" + forceDisplay",
                        "\"activityId\"",
                        "\"productId\"",
                        "\"relationId\"",
                        "\"occurredAt\"",
                        "outboxEventAppender.appendIfAbsent(",
                        "ProductDomainEventTypes.PRODUCT_LISTED",
                        "ProductDomainEventTypes.ACTIVITY_SYNC_COMPLETED",
                        "ProductDomainEventTypes.PARTNER_SYNC_COMPLETED");

        assertThat(productRouter)
                .contains(
                        "eventType.startsWith(\"Product\")",
                        "eventType.startsWith(\"Activity\")",
                        "eventType.startsWith(\"Partner\")",
                        "eventType.startsWith(\"Colonel\")",
                        "productDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload())");

        assertThat(productTest)
                .contains(
                        "publishProductListed_shouldAppendOutboxEvent",
                        "publishProductHidden_shouldAppendOutboxWithReason",
                        "publishActivitySyncCompleted_shouldEmitSpringEventWithPayload",
                        "publishPartnerSyncCompleted_shouldEmitSpringEventWithPayload",
                        "publishActivitySyncCompleted_shouldSwallowSpringPublisherFailure");

        assertThat(appender)
                .contains(
                        "public UUID appendIfAbsent(",
                        "eq(DomainEventOutbox::getEventKey, eventKey)",
                        "return existing.getEventId();",
                        "outbox.setEventKey(eventKey)",
                        "outbox.setEventVersion(eventVersion)",
                        "outbox.setStatus(DomainEventStatus.PENDING.name())",
                        "outbox.setRetryCount(0)",
                        "outbox.setMaxRetry(5)");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
