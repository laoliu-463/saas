package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统菜单
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends com.colonel.saas.common.base.BaseEntity {

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型：MENU / BUTTON / API */
    @TableField("menu_type")
    private String menuType = "MENU";

    /** 父菜单ID，顶级菜单为全零 UUID */
    @TableField("parent_id")
    private String parentId;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 图标 */
    private String icon;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder = 0;

    /** 权限标识 */
    @TableField("permission_code")
    private String permissionCode;

    /** 是否可见：1=是, 0=否 */
    private Integer visible = 1;

    /** 状态：1=启用, 0=禁用 */
    private Integer status = 1;
}
