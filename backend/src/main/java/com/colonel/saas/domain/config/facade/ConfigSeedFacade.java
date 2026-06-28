package com.colonel.saas.domain.config.facade;

/**
 * 配置域种子数据门面。
 * <p>用于业务域在启动期声明默认配置事实，由配置域负责是否写入
 * {@code system_config} 持久化表，避免业务域直接注入配置 Mapper。</p>
 */
public interface ConfigSeedFacade {

    /**
     * 当配置键不存在时写入 JSON 配置。
     *
     * @param configKey 配置键
     * @param value     JSON 值对象
     * @param group     配置分组
     * @param name      配置显示名
     * @return true 表示本次写入；false 表示配置已存在
     */
    boolean createJsonConfigIfMissing(String configKey, Object value, String group, String name);
}
