package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统菜单实体。
 * <p>
 * 对应数据库表：{@code sys_menu}，管理系统菜单树和按钮级权限。
 * 菜单采用树形结构，通过 parentId 关联父子关系，顶级菜单使用全零 UUID。
 * 菜单类型支持目录（MENU）、按钮（BUTTON）和接口（API）三种粒度，
 * 与角色关联后构成完整的 RBAC 权限控制体系。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see SysRoleMenu 角色-菜单关联
 * @see SysRole 系统角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends com.colonel.saas.common.base.BaseEntity {

    /**
     * 菜单名称
     * <p>对应数据库列：{@code menu_name}，菜单在前端导航中显示的名称</p>
     */
    private String menuName;

    /**
     * 菜单类型
     * <p>对应数据库列：{@code menu_type}，标识菜单节点的类型：
     * "MENU"（目录/页面菜单）、"BUTTON"（页面内按钮权限）、"API"（接口级权限）。
     * 默认值 "MENU"</p>
     */
    @TableField("menu_type")
    private String menuType = "MENU";

    /**
     * 父菜单 ID
     * <p>对应数据库列：{@code parent_id}，关联父级菜单。顶级菜单使用全零 UUID
     * （"00000000-0000-0000-0000-000000000000"）作为虚拟根节点</p>
     */
    @TableField("parent_id")
    private String parentId;

    /**
     * 路由路径
     * <p>对应数据库列：{@code path}，前端 Vue Router 的路由路径，
     * 如 "/talent/list"、"/product/manage" 等</p>
     */
    private String path;

    /**
     * 前端组件路径
     * <p>对应数据库列：{@code component}，对应的 Vue 组件路径，
     * 如 "views/TalentList.vue"，用于路由组件懒加载</p>
     */
    private String component;

    /**
     * 菜单图标
     * <p>对应数据库列：{@code icon}，前端导航菜单中显示的图标标识</p>
     */
    private String icon;

    /**
     * 排序序号
     * <p>对应数据库列：{@code sort_order}，同级菜单中的显示顺序，
     * 值越小越靠前，默认 0</p>
     */
    @TableField("sort_order")
    private Integer sortOrder = 0;

    /**
     * 权限标识
     * <p>对应数据库列：{@code permission_code}，权限编码，如 "talent:list"、
     * "product:create" 等，用于后端接口权限校验和前端按钮权限控制</p>
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 是否可见
     * <p>1=可见（在导航菜单中显示）, 0=不可见（隐藏但路由仍存在）。
     * 默认 1</p>
     */
    private Integer visible = 1;

    /**
     * 状态
     * <p>1=启用, 0=禁用。禁用后该菜单及其子菜单对用户不可访问。
     * 默认 1</p>
     */
    private Integer status = 1;
}
