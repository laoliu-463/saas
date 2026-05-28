package com.colonel.saas.domain.sample.event;

import com.colonel.saas.domain.event.DomainEventOutbox;
import org.springframework.stereotype.Component;

/**
 * 寄样域 Outbox 事件路由器。
 *
 * <p>当 Outbox 调度器从 {@code domain_event_outbox} 表中拉取到待分发事件时，
 * 通过此路由器判断事件是否属于寄样域（以 {@code "Sample"} 开头），
 * 并将其转发给 {@link SampleDomainEventPublisher#republishSpringEvent}
 * 进行 Spring 本地事件重发布。</p>
 *
 * <p>路由逻辑：
 * <ol>
 *   <li>{@link #supports(String)} 检查 eventType 是否以 {@code "Sample"} 前缀开头</li>
 *   <li>{@link #dispatch(DomainEventOutbox)} 将 Outbox 记录的 eventType 和 payload 转发</li>
 * </ol>
 *
 * @see SampleDomainEventPublisher#republishSpringEvent
 */
@Component
public class SampleDomainEventOutboxRouter {

    /** 寄样域事件发布器，用于将 Outbox 事件重发布为 Spring 本地事件。 */
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    /**
     * 构造函数，注入寄样域事件发布器。
     *
     * @param sampleDomainEventPublisher 寄样域事件发布器
     */
    public SampleDomainEventOutboxRouter(SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    /**
     * 判断给定事件类型是否属于寄样域。
     *
     * @param eventType 事件类型标识
     * @return true 表示事件类型以 {@code "Sample"} 开头，属于寄样域
     */
    public boolean supports(String eventType) {
        return eventType != null && eventType.startsWith("Sample");
    }

    /**
     * 分发寄样域 Outbox 事件，将其转为 Spring 本地事件重新发布。
     *
     * @param event Outbox 事件记录
     */
    public void dispatch(DomainEventOutbox event) {
        sampleDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload());
    }
}
