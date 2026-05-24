package com.colonel.saas.job;

import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import com.colonel.saas.domain.event.ConfigChangedEventRouter;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.domain.event.ProductDomainEventOutboxRouter;
import com.colonel.saas.domain.sample.event.SampleDomainEventOutboxRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.domain-event.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventDispatcherJob {

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 20;

    private final DomainEventOutboxService domainEventOutboxService;
    private final ConfigChangedEventRouter configChangedEventRouter;
    private final ProductDomainEventOutboxRouter productDomainEventOutboxRouter;
    private final SampleDomainEventOutboxRouter sampleDomainEventOutboxRouter;
    private final ObjectMapper objectMapper;

    public DomainEventDispatcherJob(
            DomainEventOutboxService domainEventOutboxService,
            ConfigChangedEventRouter configChangedEventRouter,
            ProductDomainEventOutboxRouter productDomainEventOutboxRouter,
            SampleDomainEventOutboxRouter sampleDomainEventOutboxRouter,
            ObjectMapper objectMapper) {
        this.domainEventOutboxService = domainEventOutboxService;
        this.configChangedEventRouter = configChangedEventRouter;
        this.productDomainEventOutboxRouter = productDomainEventOutboxRouter;
        this.sampleDomainEventOutboxRouter = sampleDomainEventOutboxRouter;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.domain-event.dispatch-interval-ms:5000}")
    @Transactional(rollbackFor = Exception.class)
    public void dispatchPendingEvents() {
        List<DomainEventOutbox> pending = domainEventOutboxService.lockPendingEvents(MAX_RETRY, BATCH_SIZE);
        for (DomainEventOutbox event : pending) {
            dispatchOne(event);
        }
    }

    private void dispatchOne(DomainEventOutbox event) {
        try {
            if (ConfigChangedEventPayload.EVENT_TYPE.equals(event.getEventType())) {
                ConfigChangedEventPayload payload = objectMapper.readValue(event.getPayload(), ConfigChangedEventPayload.class);
                configChangedEventRouter.dispatch(payload);
            } else if (productDomainEventOutboxRouter.supports(event.getEventType())) {
                productDomainEventOutboxRouter.dispatch(event);
            } else if (sampleDomainEventOutboxRouter.supports(event.getEventType())) {
                sampleDomainEventOutboxRouter.dispatch(event);
            }
            domainEventOutboxService.markPublished(event.getEventId(), event.getRetryCount() == null ? 0 : event.getRetryCount());
        } catch (Exception ex) {
            int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            int maxRetry = event.getMaxRetry() == null ? MAX_RETRY : event.getMaxRetry();
            domainEventOutboxService.markFailed(event.getEventId(), retryCount, ex.getMessage(), maxRetry);
            log.warn("Domain event dispatch failed, eventId={}, retryCount={}", event.getEventId(), retryCount, ex);
        }
    }
}
