package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.event.DomainEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventAppender {

    public static final String AGGREGATE_PRODUCT = "PRODUCT";
    public static final String AGGREGATE_ACTIVITY = "ACTIVITY";
    public static final String AGGREGATE_PARTNER = "PARTNER";
    public static final String AGGREGATE_SAMPLE = "SAMPLE";

    private final DomainEventOutboxMapper domainEventOutboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxEventAppender(DomainEventOutboxMapper domainEventOutboxMapper, ObjectMapper objectMapper) {
        this.domainEventOutboxMapper = domainEventOutboxMapper;
        this.objectMapper = objectMapper;
    }

    public UUID appendIfAbsent(
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            int eventVersion,
            Object payload,
            UUID operatorId,
            Map<String, Object> headers) {
        if (eventKey != null) {
            DomainEventOutbox existing = domainEventOutboxMapper.selectOne(new LambdaQueryWrapper<DomainEventOutbox>()
                    .eq(DomainEventOutbox::getEventKey, eventKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing.getEventId();
            }
        }
        UUID eventId = UUID.randomUUID();
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.setEventId(eventId);
        outbox.setEventKey(eventKey);
        outbox.setEventType(eventType);
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventVersion(eventVersion);
        outbox.setPayload(serialize(payload));
        outbox.setHeaders(headers == null ? null : serialize(headers));
        outbox.setStatus(DomainEventStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        outbox.setNextRetryAt(LocalDateTime.now());
        outbox.setOccurredAt(LocalDateTime.now());
        outbox.setCreatedBy(operatorId == null ? null : operatorId.toString());
        domainEventOutboxMapper.insert(outbox);
        return eventId;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
