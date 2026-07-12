package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceSummaryRefreshedEventContractTest {

    @Test
    void performanceSummaryRefreshedEventShouldCarrySummaryPayload() throws IOException {
        String event = readProjectFile(
                "src/main/java/com/colonel/saas/event/PerformanceSummaryRefreshedEvent.java");

        assertThat(event)
                .contains(
                        "public record PerformanceSummaryRefreshedEvent",
                        "UUID eventId",
                        "String orderId",
                        "LocalDate statDate",
                        "String period",
                        "UUID ownerId",
                        "UUID summaryId",
                        "long orderCountDelta",
                        "long orderAmountDelta",
                        "long serviceFeeNetDelta",
                        "LocalDateTime refreshedAt");
    }

    @Test
    void dashboardSummaryRefreshShouldPublishEventAfterDailyUpsert() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java");

        assertThat(service)
                .contains(
                        "ApplicationEventPublisher eventPublisher",
                        "INSERT INTO dashboard_performance_daily",
                        "eventPublisher.publishEvent(new PerformanceSummaryRefreshedEvent(",
                        "stableEventId(statDate, event.orderId())",
                        "\"DAY\"",
                        "stableSummaryId(statDate)",
                        "orderAmountDelta",
                        "serviceFeeNet");
    }

    @Test
    void analyticsShadowShouldConsumePerformanceSummaryRefreshedEvent() throws IOException {
        String types = readProjectFile(
                "src/main/java/com/colonel/saas/domain/analytics/event/AnalyticsEventTypes.java");
        String consumer = readProjectFile(
                "src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java");
        String router = readProjectFile(
                "src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventRouter.java");
        String listener = readProjectFile(
                "src/main/java/com/colonel/saas/listener/AnalyticsShadowEventListener.java");

        assertThat(types)
                .contains("PERFORMANCE_SUMMARY_REFRESHED = \"PerformanceSummaryRefreshedEvent\"");
        assertThat(consumer)
                .contains(
                        "resolveEventId(PerformanceSummaryRefreshedEvent event)",
                        "eventTypeFor(PerformanceSummaryRefreshedEvent ignored)");
        assertThat(router)
                .contains(
                        "case AnalyticsEventTypes.PERFORMANCE_SUMMARY_REFRESHED",
                        "applyPerformanceSummaryRefresh(eventId, eventType, event)");
        assertThat(listener)
                .contains(
                        "onPerformanceSummaryRefreshed(PerformanceSummaryRefreshedEvent event)",
                        "AnalyticsEventConsumer.eventTypeFor(event)");
    }

    @Test
    void executableEvidenceShouldCoverPublishAndConsumePaths() throws IOException {
        String summaryTest = readProjectFile(
                "src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java");
        String consumerTest = readProjectFile(
                "src/test/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumerTest.java");
        String listenerTest = readProjectFile(
                "src/test/java/com/colonel/saas/listener/AnalyticsShadowEventListenerTest.java");

        assertThat(summaryTest)
                .contains(
                        "verify(eventPublisher).publishEvent(eventCaptor.capture())",
                        "published.period()).isEqualTo(\"DAY\")",
                        "published.serviceFeeNetDelta()).isEqualTo(1100L)");
        assertThat(consumerTest)
                .contains("performanceSummaryRefreshedEvent_shouldRouteToPerformanceSummaryHandler");
        assertThat(listenerTest)
                .contains("onPerformanceSummaryRefreshed_shouldDelegateWithEventIdAndSummaryRefreshedType");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
