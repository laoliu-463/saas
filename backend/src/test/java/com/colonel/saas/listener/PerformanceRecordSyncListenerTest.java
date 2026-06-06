package com.colonel.saas.listener;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.PerformanceCalculationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceRecordSyncListenerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private PerformanceCalculationService performanceCalculationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PerformanceRecordSyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new PerformanceRecordSyncListener(
                orderMapper,
                performanceCalculationService,
                eventPublisher);
    }

    @Test
    void onOrderSynced_shouldUpsertPerformanceRecordAndPublishCalculatedEventWhenOrderExists() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-1");
        ColonelsettlementOrder order = order("ORD-LISTENER-1");
        PerformanceRecord record = performanceRecord("ORD-LISTENER-1", 123L, 45L, false);
        when(orderMapper.findByOrderId("ORD-LISTENER-1")).thenReturn(order);
        when(performanceCalculationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderSynced(event);

        verify(performanceCalculationService).upsertFromOrder(order);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PerformanceCalculatedEvent.class);
        PerformanceCalculatedEvent calculatedEvent = (PerformanceCalculatedEvent) eventCaptor.getValue();
        assertThat(calculatedEvent.orderId()).isEqualTo("ORD-LISTENER-1");
        assertThat(calculatedEvent.estimateGrossProfit()).isEqualTo(123L);
        assertThat(calculatedEvent.effectiveGrossProfit()).isEqualTo(45L);
        assertThat(calculatedEvent.reversed()).isFalse();
    }

    @Test
    void onOrderSynced_shouldDelegateDuplicateEventsToUpsertWithoutCreatingSeparatePath() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-DUP");
        ColonelsettlementOrder order = order("ORD-LISTENER-DUP");
        PerformanceRecord record = performanceRecord("ORD-LISTENER-DUP", 100L, 80L, false);
        when(orderMapper.findByOrderId("ORD-LISTENER-DUP")).thenReturn(order);
        when(performanceCalculationService.upsertFromOrder(order)).thenReturn(record);

        listener.onOrderSynced(event);
        listener.onOrderSynced(event);

        verify(orderMapper, times(2)).findByOrderId("ORD-LISTENER-DUP");
        verify(performanceCalculationService, times(2)).upsertFromOrder(order);
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void onOrderSynced_shouldNotCalculatePerformanceWhenOrderIsStillMissing() {
        OrderSyncedEvent event = orderSynced("ORD-LISTENER-MISSING");
        when(orderMapper.findByOrderId("ORD-LISTENER-MISSING")).thenReturn(null);

        listener.onOrderSynced(event);

        verify(performanceCalculationService, never()).upsertFromOrder(any());
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

    private ColonelsettlementOrder order(String orderId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(orderId);
        order.setOrderStatus(1);
        return order;
    }

    private PerformanceRecord performanceRecord(
            String orderId,
            Long estimateGrossProfit,
            Long effectiveGrossProfit,
            boolean reversed) {
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId(orderId);
        record.setEstimateGrossProfit(estimateGrossProfit);
        record.setEffectiveGrossProfit(effectiveGrossProfit);
        record.setReversed(reversed);
        return record;
    }
}
