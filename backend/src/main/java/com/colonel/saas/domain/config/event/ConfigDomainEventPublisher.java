package com.colonel.saas.domain.config.event;

/**
 * 配置领域事件发布器接口（DDD-CONFIG-004）。
 * <p>
 * 定义系统配置变更后发布领域事件的契约。
 * </p>
 */
public interface ConfigDomainEventPublisher {

    /**
     * 发布配置更新领域事件。
     *
     * @param event 配置更新事件对象
     */
    void publish(ConfigUpdatedEvent event);
}
