package com.colonel.saas.domain.config.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 配置项变更的领域事件负载数据（DDD-CONFIG-004）。
 * <p>
 * 当配置项被新建、更新或逻辑删除时发布，包含必要元数据。
 * 支持 Jackson 序列化与反序列化，供进程内及后续 Outbox 机制传输使用。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigUpdatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置变更事件的唯一标识 ID */
    private UUID eventId;

    /** 配置项的全局唯一键（如: douyin.app.key） */
    private String configKey;

    /** 配置项变更前的值（新建时为 null） */
    private String oldValue;

    /** 配置项变更后的值（删除时为 null） */
    private String newValue;

    /** 配置项的值类型（如: STRING, INT, DECIMAL, BOOLEAN 等，默认 STRING） */
    private String valueType;

    /** 操作本次配置变更的用户 ID */
    private UUID operatorId;

    /** 配置项最终变更持久化的系统时间 */
    private LocalDateTime updatedAt;
}
