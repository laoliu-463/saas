package com.colonel.saas.domain.order.event;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.OrderDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.event.OrderSyncedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InProcessOrderDomainEventPublisherTest {

    @Mock
    private OutboxEventAppender outboxEventAppender;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private InProcessOrderDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        DddRefactorProperties properties = new DddRefactorProperties();
        properties.setEnabled(true);
        properties.getOutbox().setEnabled(true);
        publisher = new InProcessOrderDomainEventPublisher(
                outboxEventAppender,
                applicationEventPublisher,
                new ObjectMapper(),
                properties);
    }

    @Test
    void publishOrderAttributionReplayed_shouldUseOrderVersionInOutboxKey() {
        UUID rowId = UUID.randomUUID();

        publisher.publishOrderAttributionReplayed(new OrderAttributionReplayedEvent("ORD-REPLAY-1", rowId, 3));
        publisher.publishOrderAttributionReplayed(new OrderAttributionReplayedEvent("ORD-REPLAY-1", rowId, 3));
        publisher.publishOrderAttributionReplayed(new OrderAttributionReplayedEvent("ORD-REPLAY-1", rowId, 4));

        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventAppender, org.mockito.Mockito.times(3)).appendIfAbsent(
                eventKeyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(OrderDomainEventTypes.ORDER_ATTRIBUTION_REPLAYED),
                org.mockito.ArgumentMatchers.eq(OutboxEventAppender.AGGREGATE_ORDER),
                org.mockito.ArgumentMatchers.eq("ORD-REPLAY-1"),
                anyInt(),
                any(),
                any(),
                any());
        assertThat(eventKeyCaptor.getAllValues()).containsExactly(
                "OrderAttributionReplayed:ORD-REPLAY-1:3",
                "OrderAttributionReplayed:ORD-REPLAY-1:3",
                "OrderAttributionReplayed:ORD-REPLAY-1:4");
    }

    @Test
    void publishOrderSynced_shouldUseOrderVersionInOutboxKey() {
        UUID rowId = UUID.randomUUID();
        OrderSyncedEvent event = OrderSyncedEvent.versioned("ORD-SYNC-1", rowId, 9);

        publisher.publishOrderSynced(event);

        verify(outboxEventAppender).appendIfAbsent(
                org.mockito.ArgumentMatchers.eq("OrderSynced:ORD-SYNC-1:9"),
                org.mockito.ArgumentMatchers.eq(OrderDomainEventTypes.ORDER_SYNCED),
                org.mockito.ArgumentMatchers.eq(OutboxEventAppender.AGGREGATE_ORDER),
                org.mockito.ArgumentMatchers.eq("ORD-SYNC-1"),
                anyInt(),
                org.mockito.ArgumentMatchers.eq(event),
                any(),
                any());
    }

    @Test
    void appendOrderAttributionReplayedInTransaction_shouldPropagateOutboxFailure() {
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxEventAppender).appendIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyInt(), any(), any(), any());

        assertThatThrownBy(() -> publisher.appendOrderAttributionReplayedInTransaction(
                "OrderAttributionReplayed:ORD-REPLAY-FAIL:1",
                new OrderAttributionReplayedEvent("ORD-REPLAY-FAIL", UUID.randomUUID(), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");
    }

    @Test
    void republishSpringEvent_shouldDeserializeAttributionReplayEvent() throws Exception {
        UUID rowId = UUID.randomUUID();
        String payload = new ObjectMapper().writeValueAsString(
                new OrderAttributionReplayedEvent("ORD-REPLAY-2", rowId, 5));

        publisher.republishSpringEvent(OrderDomainEventTypes.ORDER_ATTRIBUTION_REPLAYED, payload);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(
                new OrderAttributionReplayedEvent("ORD-REPLAY-2", rowId, 5));
    }

    @Test
    void republishSpringEvent_shouldPropagateConsumerFailureForOutboxRetry() throws Exception {
        UUID rowId = UUID.randomUUID();
        String payload = new ObjectMapper().writeValueAsString(
                new OrderAttributionReplayedEvent("ORD-REPLAY-FAIL", rowId, 6));
        doThrow(new IllegalStateException("execution ledger unavailable"))
                .when(applicationEventPublisher).publishEvent(any(Object.class));

        assertThatThrownBy(() -> publisher.republishSpringEvent(
                OrderDomainEventTypes.ORDER_ATTRIBUTION_REPLAYED, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order domain event republish failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
