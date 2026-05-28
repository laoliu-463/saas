package com.colonel.saas.vo;

import lombok.Data;

import java.util.UUID;

/**
 * 系统角色展示视图对象。
 * <p>
 * 用于角色管理页面中角色信息的展示，对应 {@code sys_role} 表的数据。
 * 在权限管理模块中，角色是数据范围控制和菜单权限分配的核心实体。
 * </p>
 *
 * @see com.colonel.saas.constant.RoleCodes
 * @see com.colonel.saas.mapper.SysRoleMapper
 */
@Data
public class SysRoleVO {
    /** 角色唯一标识 */
    private UUID id;
    /** 角色编码，如 admin、biz_leader 等 */
    private String roleCode;
    /** 角色名称，用于前端展示 */
    private String roleName;
    /**
     * 数据范围：
     * <ul>
     *   <li>1 — 全部数据</li>
     *   <li>2 — 本部门及以下</li>
     *   <li>3 — 本部门</li>
     *   <li>4 — 仅本人</li>
     *   <li>5 — 自定义</li>
     * </ul>
     */
    private Integer dataScope;
    /** 角色状态：1-启用，0-禁用 */
    private Integer status;
    /** 角色备注说明 */
    private String remark;
}
