package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 系统角色实体。
 * <p>
 * 对应数据库表：{@code sys_role}，定义平台的角色及其权限配置。
 * 角色通过 {@link SysUserRole} 与用户关联，通过 dataScope 控制数据范围（self / group / all）。
 * permissions 存储操作权限（JSON 对象），menuConfig 存储可见菜单配置（JSON 对象）。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see SysUser 系统用户
 * @see SysUserRole 用户-角色关联
 * @see SysDept 部门实体，dataScope 为 group 时使用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends com.colonel.saas.common.base.BaseEntity {

    /**
     * 角色代码
     * <p>对应数据库列：{@code role_code}，角色的唯一业务编码，如 "ADMIN"、"OPS_LEAD" 等</p>
     */
    private String roleCode;

    /**
     * 角色名称
     * <p>对应数据库列：{@code role_name}，角色的中文显示名称，如"管理员"、"运营主管"等</p>
     */
    private String roleName;

    /**
     * 数据范围
     * <p>对应数据库列：{@code data_scope}，控制角色可访问的数据范围。
     * 1=仅自己（self），2=本组（group，按部门过滤），3=全部（all）</p>
     */
    @TableField("data_scope")
    private Integer dataScope = 1;

    /**
     * 操作权限配置
     * <p>对应数据库列：{@code permissions}，JSON 格式存储角色的操作权限映射，
     * 如按钮级权限、API 访问权限等</p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> permissions;

    /**
     * 可见菜单配置
     * <p>对应数据库列：{@code menu_config}，JSON 格式存储角色可见的菜单 ID 列表及配置，
     * 前端根据此配置动态渲染侧边栏菜单</p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> menuConfig;

    /**
     * 角色状态
     * <p>对应数据库列：{@code status}，1=启用, 0=禁用。禁用的角色不参与权限校验</p>
     */
    @TableField("status")
    private Integer status = 1;

    /**
     * 备注
     * <p>对应数据库列：{@code remark}，角色的额外说明信息</p>
     */
    private String remark;
}
