package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceCalculatedEventContractTest {

    @Test
    void performanceCalculatedEventShouldCarryAttributionCommissionGrossProfitAndCorrectionPayload() throws IOException {
        String event = readProjectFile("src/main/java/com/colonel/saas/event/PerformanceCalculatedEvent.java");

        assertThat(event)
                .contains(
                        "record PerformanceCalculatedEvent",
                        "String orderId",
                        "UUID finalChannelUserId",
                        "UUID finalRecruiterUserId",
                        "long estimateRecruiterCommission",
                        "long effectiveRecruiterCommission",
                        "long estimateChannelCommission",
                        "long effectiveChannelCommission",
                        "long estimateGrossProfit",
                        "long effectiveGrossProfit",
                        "String correctionType",
                        "boolean reversed",
                        "reversed ? \"REVERSAL\" : \"NORMAL\"");
    }

    @Test
    void performanceRecordSyncListenerShouldPublishCalculatedEventFromPersistedPerformanceRecord() throws IOException {
        String listener = readProjectFile("src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java");

        assertThat(listener)
                .contains(
                        "@EventListener",
                        "orderReadFacade.findByOrderId(event.orderId())",
                        "performanceCalculationApplicationService.upsertFromOrder(order)",
                        "eventPublisher.publishEvent(new PerformanceCalculatedEvent(",
                        "record.getFinalChannelUserId()",
                        "record.getFinalRecruiterUserId()",
                        "record.getEstimateRecruiterCommission()",
                        "record.getEffectiveRecruiterCommission()",
                        "record.getEstimateChannelCommission()",
                        "record.getEffectiveChannelCommission()",
                        "record.getEstimateGrossProfit()",
                        "record.getEffectiveGrossProfit()",
                        "\"REVERSAL\" : \"NORMAL\"");
    }

    @Test
    void analyticsShadowShouldConsumePerformanceCalculatedEventAsPerformanceSummary() throws IOException {
        String listener = readProjectFile("src/main/java/com/colonel/saas/listener/AnalyticsShadowEventListener.java");
        String consumer = readProjectFile("src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java");
        String router = readProjectFile("src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventRouter.java");
        String aggregation = readProjectFile("src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsAggregationService.java");

        assertThat(listener)
                .contains(
                        "public void onPerformanceCalculated(PerformanceCalculatedEvent event)",
                        "AnalyticsEventConsumer.resolveEventId(event)",
                        "AnalyticsEventConsumer.eventTypeFor(event)");
        assertThat(consumer)
                .contains(
                        "\"PerformanceCalculated:\" + event.orderId()",
                        "return AnalyticsEventTypes.PERFORMANCE_CALCULATED");
        assertThat(router)
                .contains(
                        "case AnalyticsEventTypes.PERFORMANCE_CALCULATED",
                        "payload instanceof PerformanceCalculatedEvent event",
                        "aggregationService.applyPerformanceSummary(eventId, eventType, event)");
        assertThat(aggregation)
                .contains(
                        "applyPerformanceSummary",
                        "performanceSummaryInvocations.incrementAndGet()",
                        "AnalyticsHandlerType.PERFORMANCE_SUMMARY");
    }

    @Test
    void executableEvidenceShouldCoverPublishAndConsumePaths() throws IOException {
        String syncListenerTest = readProjectFile("src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java");
        String analyticsListenerTest = readProjectFile("src/test/java/com/colonel/saas/listener/AnalyticsShadowEventListenerTest.java");
        String analyticsConsumerTest = readProjectFile(
                "src/test/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumerTest.java");

        assertThat(syncListenerTest)
                .contains(
                        "onOrderSynced_shouldUpsertPerformanceRecordAndPublishCalculatedEventWhenOrderExists",
                        "assertThat(calculatedEvent.finalChannelUserId()).isEqualTo(finalChannelUserId)",
                        "assertThat(calculatedEvent.estimateRecruiterCommission()).isEqualTo(12L)",
                        "assertThat(calculatedEvent.correctionType()).isEqualTo(\"NORMAL\")",
                        "onOrderSynced_shouldPublishReversedCalculatedEventWhenRefundedOrderIsConsumed",
                        "assertThat(calculatedEvent.correctionType()).isEqualTo(\"REVERSAL\")");
        assertThat(analyticsListenerTest)
                .contains(
                        "onPerformanceCalculated_shouldDelegateWithNameBasedIdAndPerformanceCalculatedType",
                        "AnalyticsEventTypes.PERFORMANCE_CALCULATED",
                        "AnalyticsHandlerType.PERFORMANCE_SUMMARY");
        assertThat(analyticsConsumerTest)
                .contains(
                        "performanceEvent_shouldRouteToPerformanceSummaryHandler",
                        "assertThat(result.handlerType()).isEqualTo(AnalyticsHandlerType.PERFORMANCE_SUMMARY)");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
