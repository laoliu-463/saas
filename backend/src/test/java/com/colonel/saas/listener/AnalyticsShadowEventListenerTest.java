package com.colonel.saas.listener;

import com.colonel.saas.domain.analytics.application.AggregationUpdateResult;
import com.colonel.saas.domain.analytics.application.AnalyticsEventConsumer;
import com.colonel.saas.domain.analytics.application.AnalyticsHandlerType;
import com.colonel.saas.domain.analytics.event.AnalyticsEventTypes;
import com.colonel.saas.event.OrderSyncedEvent;
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
}
