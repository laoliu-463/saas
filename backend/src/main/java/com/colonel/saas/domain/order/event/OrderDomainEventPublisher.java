package com.colonel.saas.domain.order.event;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.OrderDomainEventTypes;
import com.colonel.saas.domain.event.OutboxEventAppender;
import com.colonel.saas.event.OrderSyncedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 订单域事件发布器（DDD-OUTBOX-001）。
 *
 * <p>开关 {@code ddd.refactor.outbox.enabled=true} 且根开关开启时，
 * {@link OrderSyncedEvent} 仅写入 Outbox，由 {@link com.colonel.saas.job.DomainEventDispatcherJob}
 * 异步重发布；关闭时保持事务提交后直接发布 Spring 本地事件。</p>
 */
@Service
public class OrderDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderDomainEventPublisher.class);
    private static final int EVENT_VERSION = 1;

    private final OutboxEventAppender outboxEventAppender;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final DddRefactorProperties dddRefactorProperties;

    public OrderDomainEventPublisher(
            OutboxEventAppender outboxEventAppender,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            DddRefactorProperties dddRefactorProperties) {
        this.outboxEventAppender = outboxEventAppender;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    public boolean isOutboxRoutingEnabled() {
        return dddRefactorProperties.isEnabled() && dddRefactorProperties.getOutbox().isEnabled();
    }

    /** 在业务事务内写入 Outbox（幂等键去重）。 */
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

    /** 事务提交后直接发布 Spring 本地事件（legacy 路径）。 */
    public void publishOrderSyncedDirect(OrderSyncedEvent event) {
        if (event == null) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring local OrderSyncedEvent publish failed: orderId={}", event.orderId(), ex);
        }
    }

    /** Outbox 路由器回调：将 JSON 载荷还原为 {@link OrderSyncedEvent} 并发布。 */
    public void republishSpringEvent(String eventType, String payloadJson) {
        if (!OrderDomainEventTypes.ORDER_SYNCED.equals(eventType)) {
            return;
        }
        try {
            OrderSyncedEvent event = objectMapper.readValue(payloadJson, OrderSyncedEvent.class);
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Spring republish failed for eventType={}", eventType, ex);
        }
    }
}
