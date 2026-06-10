package com.colonel.saas.domain.config.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 进程内配置领域事件分发器（DDD-CONFIG-004）。
 * <p>
 * 同步将事件分发至 Spring ApplicationEvent 和所有实现 {@link ConfigCacheRefresher} 接口的监听器。
 * 严格捕获并隔离监听器的任何异常，保证不因事件处理失败而破坏主流程的配置保存事务。
 * </p>
 */
@Slf4j
@Component
public class InProcessConfigDomainEventPublisher implements ConfigDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final List<ConfigCacheRefresher> refreshers;

    public InProcessConfigDomainEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            List<ConfigCacheRefresher> refreshers) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.refreshers = refreshers != null ? refreshers : Collections.emptyList();
    }

    @Override
    public void publish(ConfigUpdatedEvent event) {
        if (event == null) {
            return;
        }

        log.info("Publishing config updated event for key: {}", event.getConfigKey());

        // 1. 发布 Spring Application Event
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish spring event for config: {}", event.getConfigKey(), e);
        }

        // 2. 依次同步触发刷新监听器接口，并捕获一切异常
        for (ConfigCacheRefresher refresher : refreshers) {
            try {
                refresher.onConfigUpdated(event);
            } catch (Exception e) {
                log.error("ConfigCacheRefresher [{}] threw an exception on key [{}]",
                        refresher.getClass().getName(), event.getConfigKey(), e);
            }
        }
    }
}
