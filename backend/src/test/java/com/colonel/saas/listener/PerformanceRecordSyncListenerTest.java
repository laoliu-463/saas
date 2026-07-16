package com.colonel.saas.listener;

import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.domain.order.event.OrderAttributionReplayedEvent;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.performance.application.PerformanceCalculationApplicationService;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceRecordSyncListenerTest {

    @Mock
    private OrderReadFacade orderReadFacade;
    @Mock
    private PerformanceCalculationApplicationService performanceCalculationApplicationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PerformanceRecordSyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new PerformanceRecordSyncListener(
                orderReadFacade,
                performanceCalculationApplicationService,
                eventPublisher);
    }

    @Test
    void onOrderSynced_shouldUpsertPerformanceRecordAndPublishCalculatedEventWhenOrderExists() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-1");
        ColonelsettlementOrder order = order("ORD-LISTENER-1");
        UUID finalChannelUserId = UUID.randomUUID();
        UUID finalRecruiterUserId = UUID.randomUUID();
        PerformanceRecord record = performanceRecord(
                "ORD-LISTENER-1",
                finalChannelUserId,
                finalRecruiterUserId,
                12L,
                10L,
                34L,
                30L,
                123L,
                45L,
                false);
        when(orderReadFacade.findByOrderId("ORD-LISTENER-1")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderSynced(event);

        verify(performanceCalculationApplicationService).upsertFromOrder(order);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PerformanceCalculatedEvent.class);
        PerformanceCalculatedEvent calculatedEvent = (PerformanceCalculatedEvent) eventCaptor.getValue();
        assertThat(calculatedEvent.orderId()).isEqualTo("ORD-LISTENER-1");
        assertThat(calculatedEvent.finalChannelUserId()).isEqualTo(finalChannelUserId);
        assertThat(calculatedEvent.finalRecruiterUserId()).isEqualTo(finalRecruiterUserId);
        assertThat(calculatedEvent.estimateRecruiterCommission()).isEqualTo(12L);
        assertThat(calculatedEvent.effectiveRecruiterCommission()).isEqualTo(10L);
        assertThat(calculatedEvent.estimateChannelCommission()).isEqualTo(34L);
        assertThat(calculatedEvent.effectiveChannelCommission()).isEqualTo(30L);
        assertThat(calculatedEvent.estimateGrossProfit()).isEqualTo(123L);
        assertThat(calculatedEvent.effectiveGrossProfit()).isEqualTo(45L);
        assertThat(calculatedEvent.correctionType()).isEqualTo("NORMAL");
        assertThat(calculatedEvent.reversed()).isFalse();
    }

    @Test
    void onOrderSynced_shouldPublishReversedCalculatedEventWhenRefundedOrderIsConsumed() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-REFUND");
        ColonelsettlementOrder order = order("ORD-LISTENER-REFUND");
        order.setOrderStatus(5);
        PerformanceRecord record = performanceRecord(
                "ORD-LISTENER-REFUND",
                UUID.randomUUID(),
                UUID.randomUUID(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                true);
        when(orderReadFacade.findByOrderId("ORD-LISTENER-REFUND")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderSynced(event);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PerformanceCalculatedEvent calculatedEvent = (PerformanceCalculatedEvent) eventCaptor.getValue();
        assertThat(calculatedEvent.orderId()).isEqualTo("ORD-LISTENER-REFUND");
        assertThat(calculatedEvent.correctionType()).isEqualTo("REVERSAL");
        assertThat(calculatedEvent.reversed()).isTrue();
        assertThat(calculatedEvent.estimateGrossProfit()).isZero();
        assertThat(calculatedEvent.effectiveGrossProfit()).isZero();
    }

    @Test
    void onOrderRefundFactSynced_shouldUpsertRefundedOrderAndPublishReversalEvent() {
        OrderRefundFactSyncedEvent event = orderRefundFactSynced("ORD-REFUND-FACT");
        ColonelsettlementOrder order = order("ORD-REFUND-FACT");
        order.setOrderStatus(5);
        PerformanceRecord record = performanceRecord(
                "ORD-REFUND-FACT",
                UUID.randomUUID(),
                UUID.randomUUID(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                true);
        when(orderReadFacade.findByOrderId("ORD-REFUND-FACT")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderRefundFactSynced(event);

        verify(performanceCalculationApplicationService).upsertFromOrder(order);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PerformanceCalculatedEvent calculatedEvent = (PerformanceCalculatedEvent) eventCaptor.getValue();
        assertThat(calculatedEvent.orderId()).isEqualTo("ORD-REFUND-FACT");
        assertThat(calculatedEvent.correctionType()).isEqualTo("REVERSAL");
        assertThat(calculatedEvent.reversed()).isTrue();
    }

    @Test
    void onOrderAttributionReplayed_shouldReadLatestFactUpsertAndPublishCalculatedEvent() {
        ColonelsettlementOrder order = order("ORD-ATTRIBUTION-REPLAY");
        PerformanceRecord record = performanceRecord(
                "ORD-ATTRIBUTION-REPLAY",
                UUID.randomUUID(),
                UUID.randomUUID(),
                12L,
                10L,
                34L,
                30L,
                123L,
                45L,
                false);
        when(orderReadFacade.findByOrderId("ORD-ATTRIBUTION-REPLAY")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderAttributionReplayed(new OrderAttributionReplayedEvent(
                "ORD-ATTRIBUTION-REPLAY", order.getId(), 7));

        verify(performanceCalculationApplicationService).upsertFromOrder(order);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PerformanceCalculatedEvent.class);
        assertThat(((PerformanceCalculatedEvent) eventCaptor.getValue()).orderId())
                .isEqualTo("ORD-ATTRIBUTION-REPLAY");
    }

    @Test
    void onOrderAttributionReplayed_shouldPropagateCalculationFailureForOutboxRetry() {
        ColonelsettlementOrder order = order("ORD-ATTRIBUTION-REPLAY-FAIL");
        when(orderReadFacade.findByOrderId("ORD-ATTRIBUTION-REPLAY-FAIL")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order))
                .thenThrow(new IllegalStateException("calculation failed"));

        assertThatThrownBy(() -> listener.onOrderAttributionReplayed(new OrderAttributionReplayedEvent(
                "ORD-ATTRIBUTION-REPLAY-FAIL", order.getId(), 8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("calculation failed");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void onOrderSynced_shouldDelegateDuplicateEventsToUpsertWithoutCreatingSeparatePath() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-DUP");
        ColonelsettlementOrder order = order("ORD-LISTENER-DUP");
        PerformanceRecord record = performanceRecord(
                "ORD-LISTENER-DUP",
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                8L,
                20L,
                18L,
                100L,
                80L,
                false);
        when(orderReadFacade.findByOrderId("ORD-LISTENER-DUP")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderSynced(event);
        listener.onOrderSynced(event);

        verify(orderReadFacade, times(2)).findByOrderId("ORD-LISTENER-DUP");
        verify(performanceCalculationApplicationService, times(2)).upsertFromOrder(order);
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void onOrderSynced_shouldNotCalculatePerformanceWhenOrderIsStillMissing() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-MISSING");
        when(orderReadFacade.findByOrderId("ORD-LISTENER-MISSING")).thenReturn(null);

        listener.onOrderSynced(event);

        verify(performanceCalculationApplicationService, never()).upsertFromOrder(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void onOrderSynced_shouldSwallowCalculationFailureAndNotPublishEvent() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-FAIL");
        ColonelsettlementOrder order = order("ORD-LISTENER-FAIL");
        when(orderReadFacade.findByOrderId("ORD-LISTENER-FAIL")).thenReturn(order);
        when(performanceCalculationApplicationService.upsertFromOrder(order))
                .thenThrow(new IllegalStateException("calculation failed"));

        listener.onOrderSynced(event);

        verify(performanceCalculationApplicationService).upsertFromOrder(order);
        verifyNoInteractions(eventPublisher);
    }

    private OrderSyncedEvent orderSynced(String orderId) {
        return new OrderSyncedEvent(
                orderId,
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                1000L,
                1000L,
                0L,
                100L,
                0L,
                10L,
                0L,
                0L,
                0L,
                0L,
                1,
                null,
                "talent-1",
                Map.of());
    }

    private OrderRefundFactSyncedEvent orderRefundFactSynced(String orderId) {
        return new OrderRefundFactSyncedEvent(
                orderId,
                UUID.randomUUID(),
                "REF-" + orderId,
                1000L,
                3,
                5,
                "REFUND",
                Map.of(),
                null);
    }

    private ColonelsettlementOrder order(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(orderId);
        order.setOrderStatus(1);
        return order;
    }

    private PerformanceRecord performanceRecord(
            String orderId,
            UUID finalChannelUserId,
            UUID finalRecruiterUserId,
            Long estimateRecruiterCommission,
            Long effectiveRecruiterCommission,
            Long estimateChannelCommission,
            Long effectiveChannelCommission,
            Long estimateGrossProfit,
            Long effectiveGrossProfit,
            boolean reversed) {
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId(orderId);
        record.setFinalChannelUserId(finalChannelUserId);
        record.setFinalRecruiterUserId(finalRecruiterUserId);
        record.setEstimateRecruiterCommission(estimateRecruiterCommission);
        record.setEffectiveRecruiterCommission(effectiveRecruiterCommission);
        record.setEstimateChannelCommission(estimateChannelCommission);
        record.setEffectiveChannelCommission(effectiveChannelCommission);
        record.setEstimateGrossProfit(estimateGrossProfit);
        record.setEffectiveGrossProfit(effectiveGrossProfit);
        record.setReversed(reversed);
        return record;
    }
}
