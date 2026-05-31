package com.colonel.saas.constant;

/**
 * 系统角色编码常量类。
 * <p>
 * 定义了抖音团长 SaaS 系统中所有预置角色的编码标识，用于权限校验、数据范围控制、
 * 菜单分配等场景。角色编码与 {@code sys_role.role_code} 字段一一对应。
 * </p>
 * <p>
 * 预置角色（V1）：
 * <ul>
 *   <li>{@code admin} — 系统管理员，拥有全部权限</li>
 *   <li>{@code biz_leader} — 招商组长，管理招商组、审核与分配</li>
 *   <li>{@code biz_staff} — 招商专员，执行日常招商业务</li>
 *   <li>{@code channel_leader} — 渠道组长，管理渠道资源</li>
 *   <li>{@code channel_staff} — 渠道专员，执行渠道操作</li>
 *   <li>{@code ops_staff} — 运营人员，负责平台运营与发货</li>
 * </ul>
 * 历史 {@code colonel_leader} 已合并至 {@code biz_leader}（见 db/alter-role-code-merge-colonel-leader.sql）。
 * </p>
 *
 * @see com.colonel.saas.mapper.SysRoleMapper
 */
public final class RoleCodes {

    /** 防止实例化 */
    private RoleCodes() {
    }

    /** 系统管理员 — 拥有全部权限 */
    public static final String ADMIN = "admin";

    /** 招商组长 — 管理招商组，可审核、分配、查看本组数据 */
    public static final String BIZ_LEADER = "biz_leader";

    /** 招商专员 — 执行日常招商操作 */
    public static final String BIZ_STAFF = "biz_staff";

    /** 渠道组长 — 管理渠道资源，分配渠道任务 */
    public static final String CHANNEL_LEADER = "channel_leader";

    /** 渠道专员 — 执行渠道相关操作 */
    public static final String CHANNEL_STAFF = "channel_staff";

    /** 运营人员 — 负责平台运营、发货与数据维护 */
    public static final String OPS_STAFF = "ops_staff";
}
