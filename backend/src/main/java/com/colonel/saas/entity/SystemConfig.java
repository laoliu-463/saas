package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置实体。
 * <p>
 * 对应数据库表：{@code system_config}，存储平台全局配置项。
 * 配置项按 configGroup 分组，支持版本控制和规则中心展示控制。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see SystemConfigChangeLog 配置变更日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends com.colonel.saas.common.base.BaseEntity {

    /**
     * 配置键
     * <p>对应数据库列：{@code config_key}，配置项的唯一标识键，
     * 如 "sample.auto_approve_threshold" 等</p>
     */
    private String configKey;

    /**
     * 配置值
     * <p>对应数据库列：{@code config_value}，配置项的值，JSON 字符串或纯文本</p>
     */
    private String configValue;

    /**
     * 配置类型
     * <p>对应数据库列：{@code config_type}，配置值的数据类型，
     * 如 "STRING"、"INTEGER"、"BOOLEAN"、"JSON" 等</p>
     */
    private String configType;

    /**
     * 配置分组
     * <p>对应数据库列：{@code config_group}，配置项所属的业务分组，
     * 如 "SAMPLE"（寄样）、"ORDER"（订单）、"SYSTEM"（系统）等</p>
     */
    private String configGroup;

    /**
     * 配置名称
     * <p>对应数据库列：{@code config_name}，配置项的中文显示名称，用于前端展示</p>
     */
    private String configName;

    /**
     * 排序序号
     * <p>对应数据库列：{@code sort_order}，同组配置的展示顺序，数值越小越靠前</p>
     */
    @TableField("sort_order")
    private Integer sortOrder = 0;

    /**
     * 状态
     * <p>对应数据库列：{@code status}，1=正常, 0=已废弃</p>
     */
    @TableField("status")
    private Integer status = 1;

    /**
     * 备注说明
     * <p>对应数据库列：{@code remark}，配置项的额外说明信息</p>
     */
    private String remark;

    /**
     * 配置版本号
     * <p>对应数据库列：{@code config_version}，配置项的版本控制字段，
     * 每次修改自增，用于并发控制和变更追踪</p>
     */
    private Integer configVersion = 1;

    /**
     * 是否启用
     * <p>对应数据库列：{@code enabled}，true=启用, false=禁用。
     * 禁用的配置项不参与业务逻辑计算</p>
     */
    private Boolean enabled = true;

    /**
     * 是否在规则中心展示
     * <p>对应数据库列：{@code visible_in_rule_center}，控制该配置项是否在前端规则中心页面可见</p>
     */
    private Boolean visibleInRuleCenter = true;
}
