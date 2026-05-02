package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 系统角色
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends com.colonel.saas.common.base.BaseEntity {

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

    /** 备注 */
    private String remark;
}
