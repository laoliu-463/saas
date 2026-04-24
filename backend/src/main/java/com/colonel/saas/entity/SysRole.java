package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 系统角色
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends com.colonel.saas.common.base.BaseEntity {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 角色代码（唯一） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 数据范围：1=仅自己, 2=本组, 3=全部 */
    @TableField("data_scope")
    private Integer dataScope = 1;

    /** 操作权限配置（JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> permissions;

    /** 可见菜单配置（JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> menuConfig;

    /** 状态：1=启用, 0=禁用 */
    @TableField("status")
    private Integer status = 1;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted = 0;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private UUID createBy;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    /** 备注 */
    private String remark;
}
