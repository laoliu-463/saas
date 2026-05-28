package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统配置变更日志实体。
 * <p>
 * 对应数据库表：{@code system_config_change_log}，记录系统配置项的所有变更历史。
 * 不继承 BaseEntity（独立管理 ID 和时间字段），ID 由调用方指定（{@link IdType#INPUT}）。
 * 关联 {@link SystemConfig} 实体，用于审计追踪和问题排查。
 * </p>
 *
 * @see SystemConfig 系统配置实体
 */
@Data
@TableName("system_config_change_log")
public class SystemConfigChangeLog {

    /**
     * 主键 ID
     * <p>由调用方指定的 UUID 主键（{@link IdType#INPUT}），通常使用 UUID.randomUUID()</p>
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 配置项 ID
     * <p>对应数据库列：{@code config_id}，关联 {@link SystemConfig} 主键</p>
     */
    private UUID configId;

    /**
     * 配置键
     * <p>对应数据库列：{@code config_key}，冗余存储配置键，便于日志查询无需回查配置表</p>
     */
    private String configKey;

    /**
     * 变更动作
     * <p>对应数据库列：{@code change_action}，标识变更类型，
     * 如 "CREATE"（创建）、"UPDATE"（更新）、"DELETE"（删除）等</p>
     */
    private String changeAction;

    /**
     * 变更前值
     * <p>对应数据库列：{@code old_value}，配置项变更前的值（JSON 字符串或纯文本）</p>
     */
    private String oldValue;

    /**
     * 变更后值
     * <p>对应数据库列：{@code new_value}，配置项变更后的值（JSON 字符串或纯文本）</p>
     */
    private String newValue;

    /**
     * 变更来源
     * <p>对应数据库列：{@code source}，标识触发变更的渠道，
     * 如 "ADMIN_UI"（管理后台）、"API"（接口调用）、"MIGRATION"（数据迁移）等</p>
     */
    private String source;

    /**
     * 操作人 ID
     * <p>对应数据库列：{@code operator_id}，执行变更操作的用户 ID</p>
     */
    private UUID operatorId;

    /**
     * 变更时间
     * <p>对应数据库列：{@code changed_at}，配置变更发生的时间戳</p>
     */
    private LocalDateTime changedAt;

    /**
     * 关联事件 ID
     * <p>对应数据库列：{@code event_id}，如果变更由领域事件触发，记录事件 ID 用于溯源</p>
     */
    private UUID eventId;

    /**
     * 变更原因
     * <p>对应数据库列：{@code change_reason}，操作人填写的变更原因说明</p>
     */
    private String changeReason;

    /**
     * 配置版本号
     * <p>对应数据库列：{@code config_version}，变更后的配置版本号，
     * 与 {@link SystemConfig#configVersion} 对应</p>
     */
    private Integer configVersion;
}
