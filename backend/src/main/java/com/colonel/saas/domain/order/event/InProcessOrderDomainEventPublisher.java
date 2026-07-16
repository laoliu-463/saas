package com.colonel.saas.domain.order.event;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.OrderDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.event.OrderSyncedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 进程内订单事件发布具体实现类（DDD-ORDER-005）。
 */
@Component
public class InProcessOrderDomainEventPublisher implements OrderDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessOrderDomainEventPublisher.class);
    private static final int EVENT_VERSION = 1;

    private final OutboxEventAppender outboxEventAppender;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final DddRefactorProperties dddRefactorProperties;

    public InProcessOrderDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            DddRefactorProperties dddRefactorProperties) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    @Override
    public boolean isOutboxRoutingEnabled() {
        return dddRefactorProperties.isEnabled() && dddRefactorProperties.getOutbox().isEnabled();
    }

    @Override
    public void appendOrderSyncedInTransaction(String eventKey, OrderSyncedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        try {
            outboxEventAppender.appendIfAbsent(
                    eventKey,
                    OrderDomainEventTypes.ORDER_SYNCED,
                    OutboxEventAppender.AGGREGATE_ORDER,
                    event.orderId(),
                    EVENT_VERSION,
                    event,
                    null,
                    null);
        } catch (Exception ex) {
            log.warn("Outbox append failed: eventType={}, orderId={}", OrderDomainEventTypes.ORDER_SYNCED, event.orderId(), ex);
        }
    }

    @Override
    public void appendOrderAttributionReplayedInTransaction(
            String eventKey,
            OrderAttributionReplayedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        outboxEventAppender.appendIfAbsent(
                eventKey,
                OrderDomainEventTypes.ORDER_ATTRIBUTION_REPLAYED,
                OutboxEventAppender.AGGREGATE_ORDER,
                event.orderId(),
                EVENT_VERSION,
                event,
                null,
                null);
    }

    @Override
    public void appendOrderRefundFactSyncedInTransaction(String eventKey, OrderRefundFactSyncedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        try {
            outboxEventAppender.appendIfAbsent(
                    eventKey,
                    OrderDomainEventTypes.ORDER_REFUND_FACT_SYNCED,
                    OutboxEventAppender.AGGREGATE_ORDER,
                    event.orderId(),
                    EVENT_VERSION,
                    event,
                    null,
                    null);
        } catch (Exception ex) {
            log.warn("Outbox append failed: eventType={}, orderId={}",
                    OrderDomainEventTypes.ORDER_REFUND_FACT_SYNCED, event.orderId(), ex);
        }
    }

    @Override
    public void publishOrderSynced(OrderSyncedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        if (isOutboxRoutingEnabled()) {
            String eventKey = "OrderSynced:" + event.orderId() + ":" + event.orderVersion();
            appendOrderSyncedInTransaction(eventKey, event);
            return;
        }
        publishOrderSyncedDirect(event);
    }

    @Override
    public void publishOrderAttributionReplayed(OrderAttributionReplayedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        if (isOutboxRoutingEnabled()) {
            String eventKey = "OrderAttributionReplayed:" + event.orderId() + ":" + event.orderVersion();
            appendOrderAttributionReplayedInTransaction(eventKey, event);
            return;
        }
        publishOrderAttributionReplayedDirect(event);
    }

    @Override
    public void publishOrderRefundFactSynced(OrderRefundFactSyncedEvent event) {
        if (event == null || !StringUtils.hasText(event.orderId())) {
            return;
        }
        if (isOutboxRoutingEnabled()) {
            String eventKey = "OrderRefundFactSynced:" + event.orderId() + ":" + event.orderRowId();
            appendOrderRefundFactSyncedInTransaction(eventKey, event);
            return;
        }
        publishOrderRefundFactSyncedDirect(event);
    }

    @Override
    public void publishOrderSyncedDirect(OrderSyncedEvent event) {
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

    @Override
    public void publishOrderAttributionReplayedDirect(OrderAttributionReplayedEvent event) {
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

    @Override
    public void publishOrderRefundFactSyncedDirect(OrderRefundFactSyncedEvent event) {
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

    @Override
    public void publishOrderStatusChangedDirect(OrderStatusChangedEvent event) {
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

    @Override
    public void republishSpringEvent(String eventType, String payloadJson) {
        try {
            if (OrderDomainEventTypes.ORDER_SYNCED.equals(eventType)) {
                OrderSyncedEvent event = objectMapper.readValue(payloadJson, OrderSyncedEvent.class);
                applicationEventPublisher.publishEvent(event);
                return;
            }
            if (OrderDomainEventTypes.ORDER_ATTRIBUTION_REPLAYED.equals(eventType)) {
                OrderAttributionReplayedEvent event = objectMapper.readValue(
                        payloadJson, OrderAttributionReplayedEvent.class);
                applicationEventPublisher.publishEvent(event);
                return;
            }
            if (OrderDomainEventTypes.ORDER_REFUND_FACT_SYNCED.equals(eventType)) {
                OrderRefundFactSyncedEvent event = objectMapper.readValue(
                        payloadJson, OrderRefundFactSyncedEvent.class);
                applicationEventPublisher.publishEvent(event);
            }
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
            throw new IllegalStateException("Order domain event republish failed: " + eventType, ex);
        }
    }
}
