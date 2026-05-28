package com.colonel.saas.constant;

/**
 * 系统角色编码常量类。
 * <p>
 * 定义了抖音团长 SaaS 系统中所有角色的编码标识，用于权限校验、数据范围控制、
 * 菜单分配等场景。角色编码与 {@code sys_role} 表中的 {@code code} 字段一一对应。
 * </p>
 * <p>
 * 角色层级说明：
 * <ul>
 *   <li>{@code admin} — 系统管理员，拥有全部权限</li>
 *   <li>{@code biz_leader} — 业务负责人，管理业务线全局</li>
 *   <li>{@code biz_staff} — 业务人员，执行日常业务操作</li>
 *   <li>{@code channel_leader} — 渠道负责人，管理渠道资源</li>
 *   <li>{@code channel_staff} — 渠道人员，执行渠道操作</li>
 *   <li>{@code ops_staff} — 运营人员，负责平台运营</li>
 *   <li>{@code colonel_leader} — 招商组长，仅查看活动、分配商品给招商、寄样管理、数据看板，无审核权限</li>
 * </ul>
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

    /** 业务负责人 — 管理业务线全局，可审核、分配、查看数据 */
    public static final String BIZ_LEADER = "biz_leader";

    /** 业务人员 — 执行日常业务操作，如订单同步、达人管理 */
    public static final String BIZ_STAFF = "biz_staff";

    /** 渠道负责人 — 管理渠道资源，分配渠道任务 */
    public static final String CHANNEL_LEADER = "channel_leader";

    /** 渠道人员 — 执行渠道相关操作 */
    public static final String CHANNEL_STAFF = "channel_staff";

    /** 运营人员 — 负责平台运营、数据维护 */
    public static final String OPS_STAFF = "ops_staff";

    /**
     * 招商组长 — 仅查看活动、分配商品给招商、寄样管理、数据看板，无审核权限。
     * 该角色与 {@code biz_leader} 的区别在于不具备审核和全局管理能力。
     */
    public static final String COLONEL_LEADER = "colonel_leader";
}

