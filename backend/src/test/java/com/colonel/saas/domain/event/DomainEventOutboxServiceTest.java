package com.colonel.saas.domain.event;

import com.colonel.saas.config.ConfigChangedEventFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxServiceTest {

    @Mock
    private DomainEventOutboxMapper domainEventOutboxMapper;

    @Mock
    private ObjectMapper objectMapper;

    private DomainEventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new DomainEventOutboxService(domainEventOutboxMapper, objectMapper);
    }

    @Test
    void saveConfigChangedEvent_shouldInsertPendingOutboxWithFirstConfigKey() throws Exception {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID operatorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        LocalDateTime changedAt = LocalDateTime.of(2026, 5, 24, 10, 0);
        ConfigChangedEventPayload payload = payload(eventId, changedAt, List.of(
                new ConfigChangedItemPayload("sample.default.standard", "sample", "old", "new", "json", 3, List.of("sample"))));
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"eventId\":\"111\"}");

        service.saveConfigChangedEvent(payload, operatorId);

        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(domainEventOutboxMapper).insert(captor.capture());
        DomainEventOutbox outbox = captor.getValue();
        assertThat(outbox.getEventId()).isEqualTo(eventId);
        assertThat(outbox.getEventType()).isEqualTo(ConfigChangedEventPayload.EVENT_TYPE);
        assertThat(outbox.getAggregateType()).isEqualTo(ConfigChangedEventFactory.AGGREGATE_TYPE);
        assertThat(outbox.getAggregateId()).isEqualTo("sample.default.standard");
        assertThat(outbox.getStatus()).isEqualTo(DomainEventStatus.PENDING.name());
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getOccurredAt()).isEqualTo(changedAt);
        assertThat(outbox.getCreatedBy()).isEqualTo(operatorId.toString());
        assertThat(outbox.getPayload()).isEqualTo("{\"eventId\":\"111\"}");
    }

    @Test
    void saveConfigChangedEvent_shouldAllowEmptyItemsAndAnonymousOperator() throws Exception {
        ConfigChangedEventPayload payload = payload(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                LocalDateTime.of(2026, 5, 24, 11, 0),
                List.of());
        when(objectMapper.writeValueAsString(payload)).thenReturn("{}");

        service.saveConfigChangedEvent(payload, null);

        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(domainEventOutboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isNull();
        assertThat(captor.getValue().getCreatedBy()).isNull();
    }

    @Test
    void saveConfigChangedEvent_shouldWrapSerializationFailure() throws Exception {
        ConfigChangedEventPayload payload = payload(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                LocalDateTime.of(2026, 5, 24, 12, 0),
                List.of());
        when(objectMapper.writeValueAsString(payload))
                .thenThrow(new JsonProcessingException("bad json") {
                });

        assertThatThrownBy(() -> service.saveConfigChangedEvent(payload, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize config changed event");
        verify(domainEventOutboxMapper, never()).insert(any());
    }

    @Test
    void markPublished_shouldPersistPublishedStatusAndPublishedAt() {
        UUID eventId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        service.markPublished(eventId, 2);

        ArgumentCaptor<LocalDateTime> publishedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(domainEventOutboxMapper).updateDispatchResult(
                eq(eventId),
                eq(DomainEventStatus.PUBLISHED.name()),
                eq(2),
                isNull(),
                publishedAtCaptor.capture(),
                isNull());
        assertThat(publishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void markFailed_shouldScheduleRetryAndTruncateErrorBeforeMaxRetry() {
        UUID eventId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        String longError = "x".repeat(2100);

        service.markFailed(eventId, 2, longError, 5);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(domainEventOutboxMapper).updateDispatchResult(
                eq(eventId),
                eq(DomainEventStatus.FAILED.name()),
                eq(2),
                errorCaptor.capture(),
                isNull(),
                nextRetryCaptor.capture());
        assertThat(errorCaptor.getValue()).hasSize(2000);
        assertThat(nextRetryCaptor.getValue()).isNotNull();
    }

    @Test
    void markFailed_shouldMoveToDeadWhenRetryLimitReached() {
        UUID eventId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        service.markFailed(eventId, 5, null, 5);

        verify(domainEventOutboxMapper).updateDispatchResult(
                eq(eventId),
                eq(DomainEventStatus.DEAD.name()),
                eq(5),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void retryDeadEvent_shouldResetToPending() {
        UUID eventId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        service.retryDeadEvent(eventId);

        verify(domainEventOutboxMapper).resetToPending(eventId);
    }

    @Test
    void lockPendingEvents_shouldDelegateRetryAndLimit() {
        DomainEventOutbox event = event(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        when(domainEventOutboxMapper.lockPendingEvents(3, 20)).thenReturn(List.of(event));

        List<DomainEventOutbox> events = service.lockPendingEvents(3, 20);

        assertThat(events).containsExactly(event);
    }

    @Test
    void pageEvents_shouldTrimStatusAndReturnPagedRecords() {
        DomainEventOutbox event = event(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        Page<DomainEventOutbox> page = new Page<>();
        page.setRecords(List.of(event));
        when(domainEventOutboxMapper.selectPage(any(Page.class), any())).thenReturn(page);

        List<DomainEventOutbox> events = service.pageEvents(" FAILED ", 2, 15);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(domainEventOutboxMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(15);
        assertThat(events).containsExactly(event);
    }

    @Test
    void pageEvents_shouldAllowBlankStatus() {
        DomainEventOutbox event = event(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        Page<DomainEventOutbox> page = new Page<>();
        page.setRecords(List.of(event));
        when(domainEventOutboxMapper.selectPage(any(Page.class), any())).thenReturn(page);

        List<DomainEventOutbox> events = service.pageEvents(" ", 1, 10);

        assertThat(events).containsExactly(event);
    }

    @Test
    void findById_shouldDelegateSelectById() {
        UUID eventId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        DomainEventOutbox event = event(eventId);
        when(domainEventOutboxMapper.selectById(eventId)).thenReturn(event);

        assertThat(service.findById(eventId)).isSameAs(event);
    }

    private static ConfigChangedEventPayload payload(
            UUID eventId,
            LocalDateTime changedAt,
            List<ConfigChangedItemPayload> items) {
        return new ConfigChangedEventPayload(
                eventId,
                ConfigChangedEventPayload.EVENT_TYPE,
                1,
                null,
                "管理员",
                changedAt,
                "测试",
                "unit-test",
                items,
                new ConfigChangedImpactPayload(true, false, true));
    }

    private static DomainEventOutbox event(UUID eventId) {
        DomainEventOutbox event = new DomainEventOutbox();
        event.setEventId(eventId);
        event.setStatus(DomainEventStatus.PENDING.name());
        return event;
    }
}
