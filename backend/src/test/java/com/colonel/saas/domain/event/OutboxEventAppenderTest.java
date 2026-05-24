package com.colonel.saas.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventAppenderTest {

    @Mock
    private DomainEventOutboxMapper domainEventOutboxMapper;

    private OutboxEventAppender appender;

    @BeforeEach
    void setUp() {
        appender = new OutboxEventAppender(domainEventOutboxMapper, new ObjectMapper());
    }

    @Test
    void appendIfAbsent_shouldInsertWhenKeyNotExists() {
        when(domainEventOutboxMapper.selectOne(any())).thenReturn(null);

        UUID eventId = appender.appendIfAbsent(
                "ProductListed:rel-1:3",
                "ProductListedEvent",
                OutboxEventAppender.AGGREGATE_PRODUCT,
                "10001",
                1,
                Map.of("productId", "10001"),
                UUID.randomUUID(),
                null);

        assertThat(eventId).isNotNull();
        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(domainEventOutboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventKey()).isEqualTo("ProductListed:rel-1:3");
        assertThat(captor.getValue().getStatus()).isEqualTo(DomainEventStatus.PENDING.name());
    }

    @Test
    void appendIfAbsent_shouldSkipDuplicateKey() {
        DomainEventOutbox existing = new DomainEventOutbox();
        existing.setEventId(UUID.randomUUID());
        when(domainEventOutboxMapper.selectOne(any())).thenReturn(existing);

        UUID eventId = appender.appendIfAbsent(
                "ProductListed:rel-1:3",
                "ProductListedEvent",
                OutboxEventAppender.AGGREGATE_PRODUCT,
                "10001",
                1,
                Map.of("productId", "10001"),
                null,
                null);

        assertThat(eventId).isEqualTo(existing.getEventId());
        verify(domainEventOutboxMapper, never()).insert(any());
    }
}
