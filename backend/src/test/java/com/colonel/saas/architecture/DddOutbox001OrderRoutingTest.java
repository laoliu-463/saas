package com.colonel.saas.architecture;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.OrderDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.domain.order.event.InProcessOrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderDomainEventPublisher;
import com.colonel.saas.event.OrderSyncedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DddOutbox001OrderRoutingTest {

    @Mock private OutboxEventAppender outboxEventAppender;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch outboxSwitch;

    private OrderDomainEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        InProcessOrderDomainEventPublisher inProcessPublisher =
                new InProcessOrderDomainEventPublisher(applicationEventPublisher);
        publisher = new OrderDomainEventPublisher(
                outboxEventAppender,
                applicationEventPublisher,
                inProcessPublisher,
                objectMapper,
                dddRefactorProperties);
    }

    @Test
    @DisplayName("开关关闭时 appendOrderSyncedInTransaction 仍可由调用方走 direct publish")
    void publishDirect_shouldEmitSpringEventWhenSwitchOff() {
        OrderSyncedEvent event = sampleEvent();

        publisher.publishOrderSyncedDirect(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("开关开启时 appendOrderSyncedInTransaction 写入 Outbox 且不直接发 Spring 事件")
    void appendOrderSynced_shouldWriteOutboxWhenRoutingEnabled() {
        OrderSyncedEvent event = sampleEvent();
        publisher.appendOrderSyncedInTransaction("OrderSynced:ORD-1:" + event.orderRowId(), event);

        verify(outboxEventAppender).appendIfAbsent(
                eq("OrderSynced:ORD-1:" + event.orderRowId()),
                eq(OrderDomainEventTypes.ORDER_SYNCED),
                eq(OutboxEventAppender.AGGREGATE_ORDER),
                eq("ORD-1"),
                eq(1),
                eq(event),
                eq(null),
                eq(null));
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("republishSpringEvent 将 Outbox JSON 还原为 OrderSyncedEvent")
    void republishSpringEvent_shouldDeserializeOrderSyncedEvent() throws Exception {
        OrderSyncedEvent event = sampleEvent();
        String payload = objectMapper.writeValueAsString(event);

        publisher.republishSpringEvent(OrderDomainEventTypes.ORDER_SYNCED, payload);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderSyncedEvent.class);
        assertThat(((OrderSyncedEvent) captor.getValue()).orderId()).isEqualTo("ORD-1");
    }

    private static OrderSyncedEvent sampleEvent() {
        return new OrderSyncedEvent(
                "ORD-1",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                100L,
                100L,
                80L,
                10L,
                10L,
                1L,
                1L,
                5L,
                1L,
                0L,
                1,
                LocalDateTime.now(),
                "talent-1",
                null);
    }
}
