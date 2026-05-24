package com.colonel.saas.domain.event;

import com.colonel.saas.config.ConfigChangedEventFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DomainEventOutboxService {

    public static final String CONSUME_SUCCESS = "SUCCESS";
    public static final String CONSUME_FAILED = "FAILED";

    private final DomainEventOutboxMapper domainEventOutboxMapper;
    private final ObjectMapper objectMapper;

    public DomainEventOutboxService(
            DomainEventOutboxMapper domainEventOutboxMapper,
            ObjectMapper objectMapper) {
        this.domainEventOutboxMapper = domainEventOutboxMapper;
        this.objectMapper = objectMapper;
    }

    public void saveConfigChangedEvent(ConfigChangedEventPayload payload, UUID operatorId) {
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.setEventId(payload.eventId());
        outbox.setEventType(ConfigChangedEventPayload.EVENT_TYPE);
        outbox.setAggregateType(ConfigChangedEventFactory.AGGREGATE_TYPE);
        outbox.setAggregateId(payload.items().isEmpty() ? null : payload.items().get(0).configKey());
        outbox.setEventVersion(payload.eventVersion());
        outbox.setPayload(serialize(payload));
        outbox.setStatus(DomainEventStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setOccurredAt(payload.changedAt());
        outbox.setCreatedBy(operatorId == null ? null : operatorId.toString());
        domainEventOutboxMapper.insert(outbox);
    }

    public List<DomainEventOutbox> lockPendingEvents(int maxRetry, int limit) {
        return domainEventOutboxMapper.lockPendingEvents(maxRetry, limit);
    }

    public void markPublished(UUID eventId, int retryCount) {
        domainEventOutboxMapper.updateDispatchResult(
                eventId,
                DomainEventStatus.PUBLISHED.name(),
                retryCount,
                null,
                LocalDateTime.now(),
                null);
    }

    public void markFailed(UUID eventId, int retryCount, String errorMessage, int maxRetry) {
        String status = retryCount >= maxRetry
                ? DomainEventStatus.DEAD.name()
                : DomainEventStatus.FAILED.name();
        LocalDateTime nextRetry = retryCount >= maxRetry
                ? null
                : LocalDateTime.now().plusSeconds((long) Math.pow(2, Math.min(retryCount, 6)) * 5L);
        domainEventOutboxMapper.updateDispatchResult(
                eventId,
                status,
                retryCount,
                truncate(errorMessage, 2000),
                null,
                nextRetry);
    }

    public void markFailed(UUID eventId, int retryCount, String errorMessage) {
        markFailed(eventId, retryCount, errorMessage, 5);
    }

    public void retryDeadEvent(UUID eventId) {
        domainEventOutboxMapper.resetToPending(eventId);
    }

    public List<DomainEventOutbox> pageEvents(String status, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DomainEventOutbox> pager =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DomainEventOutbox> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DomainEventOutbox>()
                        .orderByDesc(DomainEventOutbox::getOccurredAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(DomainEventOutbox::getStatus, status.trim());
        }
        return domainEventOutboxMapper.selectPage(pager, wrapper).getRecords();
    }

    public DomainEventOutbox findById(UUID eventId) {
        return domainEventOutboxMapper.selectById(eventId);
    }

    private String serialize(ConfigChangedEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize config changed event", ex);
        }
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
