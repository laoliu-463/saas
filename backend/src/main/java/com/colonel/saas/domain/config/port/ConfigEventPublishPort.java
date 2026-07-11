package com.colonel.saas.domain.config.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 配置事件发布 Port（DDD-CONFIG-001 Wave 2.1 补全）。
 *
 * <p>Port-Adapter 模式入口，封装配置变更事件的发布能力。
 * Adapter 实现可选择 in-process（默认）或 outbox 持久化（未来）。</p>
 */
public interface ConfigEventPublishPort {

    /**
     * 发布一次配置变更事件（同步 in-process）。
     *
     * @param configKey 配置项 key
     * @param newValue 变更后值
     * @param oldValue 变更前值
     * @param source 变更来源（如 RULE_CENTER / ADMIN / SYSTEM）
     * @param operatorId 操作人 ID（系统时为 null）
     * @param eventId 本次事件 ID（返回以供调用方追踪）
     * @return 事件 ID（与入参一致）
     */
    UUID publishConfigChanged(
            String configKey,
            String newValue,
            String oldValue,
            String source,
            UUID operatorId,
            UUID eventId);

    /**
     * 批量发布多键变更事件。
     *
     * @param changes 多键变更内容
     * @param source 变更来源
     * @param operatorId 操作人 ID
     * @return 事件 ID 列表
     */
    List<UUID> publishBatchConfigChanged(
            Map<String, com.colonel.saas.entity.SystemConfigChangeLog> changes,
            String source,
            UUID operatorId);
}
