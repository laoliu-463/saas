package com.colonel.saas.domain.config.port;

/**
 * 配置中心仓储 Port（DDD-CONFIG-001 Wave 2.1 补全）。
 *
 * <p>Port-Adapter 模式入口，提供配置项的读写、变更日志查询与领域事件发布能力。
 * 具体实现由 {@code com.colonel.saas.infrastructure.config.adapter} 提供，
 * 通过 Spring 注入到 Application / Facade 层。</p>
 *
 * <p><b>已有：</b>{@code com.colonel.saas.mapper.SystemConfigMapper}
 * + {@code SystemConfigChangeLogMapper} 提供底层持久化能力，Port 接口封装
 * 后由 Adapter 实现映射。</p>
 */
public interface ConfigRepositoryPort {

    /**
     * 配置项仓储操作 Port 子接口（避免单接口膨胀）。
     */
    interface ConfigItemPort {
        /**
         * 按 configKey 查询当前配置值。
         *
         * @param configKey 配置项 key
         * @return 配置值（不存在时返回 null）
         */
        String findValueByKey(String configKey);
    }

    /**
     * 变更日志 Port。
     */
    interface ChangeLogPort {
        /**
         * 记录一次配置变更。
         *
         * @param changeLog 变更日志实体
         */
        void recordChange(com.colonel.saas.entity.SystemConfigChangeLog changeLog);
    }
}
