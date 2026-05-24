package com.colonel.saas.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ConfigChangedEventRouter {

    private final List<ConfigChangedEventConsumer> consumers;
    private final DomainEventConsumeLogMapper consumeLogMapper;
    private final ObjectMapper objectMapper;

    public ConfigChangedEventRouter(
            List<ConfigChangedEventConsumer> consumers,
            DomainEventConsumeLogMapper consumeLogMapper,
            ObjectMapper objectMapper) {
        this.consumers = consumers;
        this.consumeLogMapper = consumeLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void dispatch(ConfigChangedEventPayload payload) throws Exception {
        Exception lastError = null;
        for (ConfigChangedEventConsumer consumer : consumers) {
            if (!consumer.supports(payload)) {
                continue;
            }
            if (alreadyConsumed(payload.eventId(), consumer.consumerName())) {
                continue;
            }
            try {
                consumer.consume(payload, objectMapper);
                saveConsumeLog(payload.eventId(), consumer.consumerName(), DomainEventOutboxService.CONSUME_SUCCESS, null);
            } catch (Exception ex) {
                saveConsumeLog(payload.eventId(), consumer.consumerName(), DomainEventOutboxService.CONSUME_FAILED, ex.getMessage());
                lastError = ex;
                log.warn("ConfigChanged consumer failed, eventId={}, consumer={}",
                        payload.eventId(), consumer.consumerName(), ex);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private boolean alreadyConsumed(UUID eventId, String consumerName) {
        return consumeLogMapper.findSuccessful(eventId, consumerName).isPresent();
    }

    private void saveConsumeLog(UUID eventId, String consumerName, String status, String errorMessage) {
        DomainEventConsumeLog logEntry = new DomainEventConsumeLog();
        logEntry.setId(UUID.randomUUID());
        logEntry.setEventId(eventId);
        logEntry.setConsumerName(consumerName);
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setConsumedAt(LocalDateTime.now());
        logEntry.setCreatedAt(LocalDateTime.now());
        consumeLogMapper.insert(logEntry);
    }
}
