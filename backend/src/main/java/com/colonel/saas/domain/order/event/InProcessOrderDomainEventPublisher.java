package com.colonel.saas.domain.order.event;

import com.colonel.saas.event.OrderSyncedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 进程内订单事件发布器（DDD-ORDER-005）。
 */
@Component
public class InProcessOrderDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessOrderDomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public InProcessOrderDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishAfterCommit(OrderSyncedEvent event) {
        if (event == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            applicationEventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applicationEventPublisher.publishEvent(event);
            }
        });
    }

    public void publishStatusChangedAfterCommit(OrderStatusChangedEvent event) {
        if (event == null) {
            return;
        }
        Runnable publish = () -> {
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception ex) {
                log.warn("OrderStatusChangedEvent publish failed orderId={}", event.orderId(), ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }
}
