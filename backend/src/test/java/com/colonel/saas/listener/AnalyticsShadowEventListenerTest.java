package com.colonel.saas.listener;

import com.colonel.saas.domain.analytics.application.AggregationUpdateResult;
import com.colonel.saas.domain.analytics.application.AnalyticsEventConsumer;
import com.colonel.saas.domain.analytics.application.AnalyticsHandlerType;
import com.colonel.saas.domain.analytics.event.AnalyticsEventTypes;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsShadowEventListenerTest {

    @Mock
    private AnalyticsEventConsumer analyticsEventConsumer;

    private AnalyticsShadowEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnalyticsShadowEventListener(analyticsEventConsumer);
    }

    @Test
    void onOrderSynced_shouldDelegateWithOrderRowIdAndOrderSyncedType() {
        UUID rowId = UUID.randomUUID();
        OrderSyncedEvent event = orderSynced("ORD-ANALYTICS-LISTENER", rowId);
        when(analyticsEventConsumer.consumeIfShadowEnabled(
                eq(rowId),
                eq(AnalyticsEventTypes.ORDER_SYNCED),
                same(event)))
                .thenReturn(AggregationUpdateResult.applied(
                        rowId,
                        AnalyticsEventTypes.ORDER_SYNCED,
                        AnalyticsHandlerType.ORDER_ESTIMATE_SUMMARY));

        listener.onOrderSynced(event);

        verify(analyticsEventConsumer).consumeIfShadowEnabled(
                rowId,
                AnalyticsEventTypes.ORDER_SYNCED,
                event);
    }

    @Test
    void onOrderSynced_shouldUseNameBasedIdWhenOrderRowIdIsMissing() {
        String orderId = "ORD-ANALYTICS-NAME-ID";
        UUID expectedEventId = UUID.nameUUIDFromBytes(
                ("OrderSynced:" + orderId).getBytes(StandardCharsets.UTF_8));
        OrderSyncedEvent event = orderSynced(orderId, null);
        when(analyticsEventConsumer.consumeIfShadowEnabled(
                eq(expectedEventId),
                eq(AnalyticsEventTypes.ORDER_SYNCED),
                same(event)))
                .thenReturn(AggregationUpdateResult.applied(
                        expectedEventId,
                        AnalyticsEventTypes.ORDER_SYNCED,
                        AnalyticsHandlerType.ORDER_ESTIMATE_SUMMARY));

        listener.onOrderSynced(event);

        verify(analyticsEventConsumer).consumeIfShadowEnabled(
                expectedEventId,
                AnalyticsEventTypes.ORDER_SYNCED,
                event);
    }

    @Test
    void onOrderRefundFactSynced_shouldDelegateWithNamespacedEventIdAndRefundFactType() {
        UUID rowId = UUID.randomUUID();
        OrderRefundFactSyncedEvent event = new OrderRefundFactSyncedEvent(
                "ORD-REFUND-SHADOW",
                rowId,
                "REF-1",
                100L,
                3,
                5,
                "REFUND",
                Map.of("refund_id", "REF-1"),
                LocalDateTime.now());
        UUID expectedEventId = UUID.nameUUIDFromBytes(
                ("OrderRefundFactSynced:" + event.orderId() + ":" + rowId)
                        .getBytes(StandardCharsets.UTF_8));
        when(analyticsEventConsumer.consumeIfShadowEnabled(
                eq(expectedEventId),
                eq(AnalyticsEventTypes.ORDER_REFUND_FACT_SYNCED),
                same(event)))
                .thenReturn(AggregationUpdateResult.applied(
                        expectedEventId,
                        AnalyticsEventTypes.ORDER_REFUND_FACT_SYNCED,
                        AnalyticsHandlerType.PERFORMANCE_SUMMARY));

        listener.onOrderRefundFactSynced(event);

        verify(analyticsEventConsumer).consumeIfShadowEnabled(
                expectedEventId,
                AnalyticsEventTypes.ORDER_REFUND_FACT_SYNCED,
                event);
    }


    @Test
    void onPerformanceCalculated_shouldDelegateWithNameBasedIdAndPerformanceCalculatedType() {
        String orderId = "ORD-PERFORMANCE-CALCULATED";
        UUID expectedEventId = UUID.nameUUIDFromBytes(
                ("PerformanceCalculated:" + orderId).getBytes(StandardCharsets.UTF_8));
        PerformanceCalculatedEvent event = new PerformanceCalculatedEvent(
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                12L,
                10L,
                34L,
                30L,
                123L,
                45L,
                "NORMAL",
                false);
        when(analyticsEventConsumer.consumeIfShadowEnabled(
                eq(expectedEventId),
                eq(AnalyticsEventTypes.PERFORMANCE_CALCULATED),
                same(event)))
                .thenReturn(AggregationUpdateResult.applied(
                        expectedEventId,
                        AnalyticsEventTypes.PERFORMANCE_CALCULATED,
                        AnalyticsHandlerType.PERFORMANCE_SUMMARY));

        listener.onPerformanceCalculated(event);

        verify(analyticsEventConsumer).consumeIfShadowEnabled(
                expectedEventId,
                AnalyticsEventTypes.PERFORMANCE_CALCULATED,
                event);
    }

    @Test
    void onPerformanceSummaryRefreshed_shouldDelegateWithEventIdAndSummaryRefreshedType() {
        UUID eventId = UUID.randomUUID();
        PerformanceSummaryRefreshedEvent event = performanceSummaryRefreshed(eventId);
        when(analyticsEventConsumer.consumeIfShadowEnabled(
                eq(eventId),
                eq(AnalyticsEventTypes.PERFORMANCE_SUMMARY_REFRESHED),
                same(event)))
                .thenReturn(AggregationUpdateResult.applied(
                        eventId,
                        AnalyticsEventTypes.PERFORMANCE_SUMMARY_REFRESHED,
                        AnalyticsHandlerType.PERFORMANCE_SUMMARY));

        listener.onPerformanceSummaryRefreshed(event);

        verify(analyticsEventConsumer).consumeIfShadowEnabled(
                eventId,
                AnalyticsEventTypes.PERFORMANCE_SUMMARY_REFRESHED,
                event);
    }

    private static OrderSyncedEvent orderSynced(String orderId, UUID rowId) {
        return new OrderSyncedEvent(
                orderId,
                rowId,
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

    private static PerformanceSummaryRefreshedEvent performanceSummaryRefreshed(UUID eventId) {
        return new PerformanceSummaryRefreshedEvent(
                eventId,
                "ORD-SUMMARY-1",
                java.time.LocalDate.of(2026, 4, 17),
                "DAY",
                null,
                UUID.randomUUID(),
                1L,
                12800L,
                1100L,
                LocalDateTime.of(2026, 4, 17, 10, 31));
    }
}
