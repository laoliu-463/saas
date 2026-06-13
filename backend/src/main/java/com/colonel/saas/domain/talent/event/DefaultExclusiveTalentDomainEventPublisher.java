package com.colonel.saas.domain.talent.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 默认事件发布实现（DDD-TALENT-004）。
 *
 * <p>直接通过 Spring {@link ApplicationEventPublisher} 同步发布；后续
 * 可切到 outbox 路由而不影响 talent 应用层。</p>
 */
@Component
public class DefaultExclusiveTalentDomainEventPublisher implements ExclusiveTalentDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public DefaultExclusiveTalentDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishActivated(ExclusiveTalentActivatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishExpired(ExclusiveTalentExpiredEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}