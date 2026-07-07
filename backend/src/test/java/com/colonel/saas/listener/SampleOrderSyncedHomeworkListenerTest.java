package com.colonel.saas.listener;

import com.colonel.saas.domain.order.application.OrderSampleHomeworkBridge;
import com.colonel.saas.event.OrderSyncedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleOrderSyncedHomeworkListenerTest {

    @Mock
    private OrderSampleHomeworkBridge orderSampleHomeworkBridge;

    private SampleOrderSyncedHomeworkListener listener;

    @BeforeEach
    void setUp() {
        listener = new SampleOrderSyncedHomeworkListener(orderSampleHomeworkBridge);
    }

    @Test
    void onOrderSynced_shouldSkipNullEventWithoutTouchingBridge() {
        listener.onOrderSynced(null);

        verifyNoInteractions(orderSampleHomeworkBridge);
    }

    @Test
    void onOrderSynced_shouldSkipWhenEventDrivenHomeworkIsDisabled() {
        when(orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()).thenReturn(false);

        listener.onOrderSynced(orderSynced());

        verify(orderSampleHomeworkBridge).isEventDrivenHomeworkEnabled();
        verify(orderSampleHomeworkBridge, never()).completeHomeworkForSyncedOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onOrderSynced_shouldDelegateToHomeworkBridgeWhenEnabled() {
        OrderSyncedEvent event = orderSynced();
        when(orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()).thenReturn(true);

        listener.onOrderSynced(event);

        verify(orderSampleHomeworkBridge).completeHomeworkForSyncedOrder(event);
    }

    @Test
    void onOrderSynced_shouldSwallowBridgeFailure() {
        OrderSyncedEvent event = orderSynced();
        when(orderSampleHomeworkBridge.isEventDrivenHomeworkEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("sample bridge failed"))
                .when(orderSampleHomeworkBridge).completeHomeworkForSyncedOrder(event);

        assertDoesNotThrow(() -> listener.onOrderSynced(event));
    }

    private static OrderSyncedEvent orderSynced() {
        return new OrderSyncedEvent(
                "ORD-SAMPLE-LISTENER",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                100L,
                100L,
                80L,
                10L,
                8L,
                2L,
                1L,
                5L,
                1L,
                0L,
                1,
                LocalDateTime.now(),
                "talent-1",
                Map.of("author_id", "talent-1"));
    }
}
