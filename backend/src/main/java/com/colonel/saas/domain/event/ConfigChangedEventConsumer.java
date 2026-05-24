package com.colonel.saas.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface ConfigChangedEventConsumer {

    String consumerName();

    boolean supports(ConfigChangedEventPayload payload);

    void consume(ConfigChangedEventPayload payload, ObjectMapper objectMapper) throws Exception;
}
