package com.colonel.saas.domain.analytics.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.analytics.event.AnalyticsEventTypes;
import com.colonel.saas.domain.analytics.infrastructure.InMemoryProcessedEventStore;
import com.colonel.saas.domain.product.event.ProductListedEvent;
import com.colonel.saas.domain.sample.event.SampleApprovedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock
    private DddRefactorProperties dddRefactorProperties;

    private InMemoryProcessedEventStore processedEventStore;
    private AnalyticsAggregationService aggregationService;
    private AnalyticsEventRouter eventRouter;
    private AnalyticsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        processedEventStore = new InMemoryProcessedEventStore();
        aggregationService = new AnalyticsAggregationService();
        eventRouter = new AnalyticsEventRouter(aggregationService);
        consumer = new AnalyticsEventConsumer(processedEventStore, eventRouter, dddRefactorProperties);
        aggregationService.resetInvocationCounters();
    }

    @Test
    void duplicateEventId_shouldProcessOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        OrderSyncedEvent event = sampleOrderSynced();

        AggregationUpdateResult first = consumer.consume(
                eventId, AnalyticsEventTypes.ORDER_SYNCED, event);
        AggregationUpdateResult second = consumer.consume(
                eventId, AnalyticsEventTypes.ORDER_SYNCED, event);

        assertThat(first.applied()).isTrue();
        assertThat(first.duplicateSkipped()).isFalse();
        assertThat(second.duplicateSkipped()).isTrue();
        assertThat(aggregationService.orderEstimateInvocationCount()).isEqualTo(1);
    }

    @Test
    void performanceEvent_shouldRouteToPerformanceSummaryHandler() {
        UUID eventId = UUID.randomUUID();
        PerformanceCalculatedEvent event = new PerformanceCalculatedEvent("ORD-P-1", 100L, 80L, false);

        AggregationUpdateResult result = consumer.consume(
                eventId, AnalyticsEventTypes.PERFORMANCE_CALCULATED, event);

        assertThat(result.handlerType()).isEqualTo(AnalyticsHandlerType.PERFORMANCE_SUMMARY);
        assertThat(aggregationService.performanceSummaryInvocationCount()).isEqualTo(1);
    }

    @Test
    void orderEvent_shouldRouteToOrderEstimateSummaryHandler() {
        UUID eventId = UUID.randomUUID();

        AggregationUpdateResult result = consumer.consume(
                eventId, AnalyticsEventTypes.ORDER_SYNCED, sampleOrderSynced());

        assertThat(result.handlerType()).isEqualTo(AnalyticsHandlerType.ORDER_ESTIMATE_SUMMARY);
        assertThat(aggregationService.orderEstimateInvocationCount()).isEqualTo(1);
    }

    @Test
    void sampleEvent_shouldRouteToSampleSummaryHandler() {
        UUID eventId = UUID.randomUUID();
        SampleApprovedEvent event = new SampleApprovedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now());

        AggregationUpdateResult result = consumer.consume(
                eventId, AnalyticsEventTypes.SAMPLE_APPROVED, event);

        assertThat(result.handlerType()).isEqualTo(AnalyticsHandlerType.SAMPLE_SUMMARY);
        assertThat(aggregationService.sampleSummaryInvocationCount()).isEqualTo(1);
    }

    @Test
    void productEvent_shouldRouteToProductSnapshotHandler() {
        UUID eventId = UUID.randomUUID();
        ProductListedEvent event = new ProductListedEvent(
                eventId,
                "act-1",
                "prod-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now(),
                "trace-1");

        AggregationUpdateResult result = consumer.consume(
                eventId, AnalyticsEventTypes.PRODUCT_LISTED, event);

        assertThat(result.handlerType()).isEqualTo(AnalyticsHandlerType.PRODUCT_SNAPSHOT);
        assertThat(aggregationService.productSnapshotInvocationCount()).isEqualTo(1);
    }

    private static OrderSyncedEvent sampleOrderSynced() {
        return new OrderSyncedEvent(
                "ORD-1",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                1000L,
                1000L,
                1000L,
                100L,
                100L,
                10L,
                10L,
                100L,
                10L,
                0L,
                1,
                LocalDateTime.now(),
                "talent-1",
                Map.of());
    }
}
