package com.colonel.saas.domain.talent.event;

/**
 * 独家达人事件发布端口（DDD-TALENT-004）。
 *
 * <p>领域应用层只通过本接口发布事件，由基础设施层选择具体的 outbox / in-memory
 * 实现，避免 talent 域直接依赖 Spring ApplicationEventPublisher。</p>
 */
public interface ExclusiveTalentDomainEventPublisher {

    void publishActivated(ExclusiveTalentActivatedEvent event);

    void publishExpired(ExclusiveTalentExpiredEvent event);
}