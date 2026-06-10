package com.colonel.saas.domain.config.event;

/**
 * 缓存刷新监听接口占位（DDD-CONFIG-004）。
 * <p>
 * 各个业务领域在实现本地配置缓存逻辑时，可通过实现此接口以接收配置更新通知。
 * </p>
 */
public interface ConfigCacheRefresher {

    /**
     * 当配置更新成功后被调用。
     *
     * @param event 配置更新事件对象
     */
    void onConfigUpdated(ConfigUpdatedEvent event);
}
