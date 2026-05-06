package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 角色-菜单关联表
 * 联合主键 (role_id, menu_id)，无独立主键
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableField("role_id")
    private UUID roleId;

    @TableField("menu_id")
    private UUID menuId;
}
