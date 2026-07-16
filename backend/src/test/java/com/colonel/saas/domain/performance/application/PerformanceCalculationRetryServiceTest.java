package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceCalculationExecution;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.domain.order.event.OrderRefundFactSyncedEvent;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationRetryServiceTest {

    @Mock private PerformanceCalculationExecutionService executionService;
    @Mock private OrderReadFacade orderReadFacade;
    @Mock private PerformanceCalculationApplicationService calculationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PerformanceRefundAdjustmentService refundAdjustmentService;

    @Test
    void retryDueShouldRecalculateAndMarkExecutionSucceeded() {
        PerformanceCalculationRetryService service = new PerformanceCalculationRetryService(
                executionService, orderReadFacade, calculationService, eventPublisher);
        PerformanceCalculationExecution execution = new PerformanceCalculationExecution();
        execution.setEventKey("OrderSynced:ORD-RETRY:5");
        execution.setEventType("OrderSynced");
        execution.setOrderId("ORD-RETRY");
        execution.setOrderVersion(5);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-RETRY");
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId("ORD-RETRY");
        record.setFinalChannelUserId(UUID.randomUUID());
        record.setFinalRecruiterUserId(UUID.randomUUID());

        when(executionService.findRetryDue(10)).thenReturn(List.of(execution));
        when(executionService.start("OrderSynced:ORD-RETRY:5", "OrderSynced", "ORD-RETRY", 5)).thenReturn(true);
        when(orderReadFacade.findByOrderId("ORD-RETRY")).thenReturn(order);
        when(calculationService.upsertFromOrder(order)).thenReturn(record);

        assertThat(service.retryDue(10).succeeded()).isEqualTo(1);

        verify(executionService).markSucceeded("OrderSynced:ORD-RETRY:5");
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOf(PerformanceCalculatedEvent.class);
    }

    @Test
    void retryDueShouldRestoreRefundAdjustmentFromExecutionPayloadBeforePublishing() {
        PerformanceCalculationRetryService service = new PerformanceCalculationRetryService(
                executionService, orderReadFacade, calculationService, eventPublisher, refundAdjustmentService);
        PerformanceCalculationExecution execution = new PerformanceCalculationExecution();
        execution.setEventKey("OrderRefundFactSynced:ORD-REFUND-RETRY:REFUND-1");
        execution.setEventType("OrderRefundFactSynced");
        execution.setOrderId("ORD-REFUND-RETRY");
        execution.setOrderVersion(9);
        execution.setEventPayload(Map.of(
                "refundId", "REFUND-1",
                "refundAmount", 250L,
                "previousStatus", 3,
                "status", 5,
                "flowPoint", "REFUND",
                "occurredAt", "2026-07-16T12:00:00"));
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-REFUND-RETRY");
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId("ORD-REFUND-RETRY");

        when(executionService.findRetryDue(10)).thenReturn(List.of(execution));
        when(executionService.start(execution.getEventKey(), execution.getEventType(), execution.getOrderId(), 9))
                .thenReturn(true);
        when(orderReadFacade.findByOrderId(execution.getOrderId())).thenReturn(order);
        when(calculationService.upsertFromOrder(order)).thenReturn(record);

        assertThat(service.retryDue(10).succeeded()).isEqualTo(1);

        ArgumentCaptor<OrderRefundFactSyncedEvent> refund = ArgumentCaptor.forClass(OrderRefundFactSyncedEvent.class);
        verify(refundAdjustmentService).recordRefund(org.mockito.ArgumentMatchers.eq(record), refund.capture());
        assertThat(refund.getValue().refundId()).isEqualTo("REFUND-1");
        assertThat(refund.getValue().refundAmount()).isEqualTo(250L);
        assertThat(refund.getValue().occurredAt()).isEqualTo(java.time.LocalDateTime.of(2026, 7, 16, 12, 0));
    }
}
