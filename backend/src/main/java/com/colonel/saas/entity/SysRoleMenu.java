package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 角色-菜单关联实体。
 * <p>
 * 对应数据库表：{@code sys_role_menu}，记录角色与菜单之间的多对多关联关系。
 * 采用联合主键 (role_id, menu_id)，无独立主键和审计字段。
 * 通过此关联表实现 RBAC 权限模型中的角色-菜单绑定，决定每个角色可以访问哪些
 * 菜单页面和操作按钮。
 * </p>
 *
 * @see SysRole 系统角色
 * @see SysMenu 系统菜单
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_role_menu")
public class SysRoleMenu {

    /**
     * 角色 ID
     * <p>对应数据库列：{@code role_id}，关联系统角色表，参与联合主键</p>
     */
    @TableField("role_id")
    private UUID roleId;

    /**
     * 菜单 ID
     * <p>对应数据库列：{@code menu_id}，关联系统菜单表，参与联合主键</p>
     */
    @TableField("menu_id")
    private UUID menuId;
}
