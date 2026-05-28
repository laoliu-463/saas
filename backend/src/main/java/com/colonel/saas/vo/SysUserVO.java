package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 系统用户展示视图对象。
 * <p>
 * 用于用户管理页面的展示，聚合了用户的账号信息、组织归属、角色信息等。
 * 该 VO 是用户信息最全面的展示对象，涵盖用户管理、权限分配、数据范围控制
 * 等多个维度的信息。
 * </p>
 * <p>
 * 关键设计说明：
 * <ul>
 *   <li>{@code deptId} — 持久化组织节点 ID（业务组或部门），用于 data_scope 本组过滤</li>
 *   <li>{@code parentDeptId/parentDeptName} — 上级部门信息，用于展示组织层级</li>
 *   <li>{@code groupId/groupName/groupType} — 所属业务组信息</li>
 *   <li>{@code forcePasswordChange} — 强制修改密码标记，首次登录或管理员重置后为 true</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.mapper.SysUserMapper
 * @see com.colonel.saas.constant.SysUserStatus
 */
@Data
public class SysUserVO {
    /** 用户唯一标识 */
    private UUID id;
    /** 登录用户名 */
    private String username;
    /** 真实姓名 */
    private String realName;
    /** 手机号码 */
    private String phone;
    /** 邮箱地址 */
    private String email;
    /** 持久化组织节点 ID（业务组或部门），用于 data_scope 本组过滤 */
    private UUID deptId;
    /** 上级部门 ID */
    private UUID parentDeptId;
    /** 上级部门名称 */
    private String parentDeptName;
    /** 所属业务组 ID */
    private UUID groupId;
    /** 所属业务组名称 */
    private String groupName;
    /** 所属业务组类型（recruiter_group / channel_group / ops_group） */
    private String groupType;
    /** 主角色 ID */
    private UUID roleId;
    /** 主角色编码 */
    private String roleCode;
    /** 主角色名称 */
    private String roleName;
    /**
     * 用户状态：
     * <ul>
     *   <li>0 — 已禁用</li>
     *   <li>1 — 正常</li>
     *   <li>2 — 待激活</li>
     * </ul>
     */
    private Integer status;
    /** 是否强制修改密码，首次登录或密码重置后为 true */
    private Boolean forcePasswordChange;
    /** 最近一次登录时间 */
    private LocalDateTime lastLoginAt;
    /** 账号创建时间 */
    private LocalDateTime createTime;
    /** 用户拥有的所有角色 ID 列表（支持多角色） */
    private List<UUID> roleIds;
}
