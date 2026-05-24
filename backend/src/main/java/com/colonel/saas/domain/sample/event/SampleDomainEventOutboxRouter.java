package com.colonel.saas.domain.sample.event;

import com.colonel.saas.domain.event.DomainEventOutbox;
import org.springframework.stereotype.Component;

@Component
public class SampleDomainEventOutboxRouter {

    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    public SampleDomainEventOutboxRouter(SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    public boolean supports(String eventType) {
        return eventType != null && eventType.startsWith("Sample");
    }

    public void dispatch(DomainEventOutbox event) {
        sampleDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload());
    }
}
