package com.colonel.saas.constant;

/**
 * 系统用户账号状态常量类。
 * <p>
 * 对应 {@code sys_user} 表中的 {@code status} 字段，定义了用户账号的三种状态。
 * 状态流转路径：待激活 → 正常 → 已禁用。
 * </p>
 * <p>
 * 状态说明：
 * <ul>
 *   <li>{@code DISABLED (0)} — 已禁用：账号被管理员禁用，无法登录</li>
 *   <li>{@code ACTIVE (1)} — 正常：账号正常可用，可正常登录和操作</li>
 *   <li>{@code PENDING_ACTIVATION (2)} — 待激活：账号已创建但尚未完成激活流程</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.mapper.SysUserMapper
 */
public final class SysUserStatus {

    /** 防止实例化 */
    private SysUserStatus() {
    }

    /** 已禁用 — 账号被管理员禁用，无法登录 */
    public static final int DISABLED = 0;

    /** 正常 — 账号状态正常，可正常登录和操作 */
    public static final int ACTIVE = 1;

    /** 待激活 — 账号已创建，等待完成激活流程 */
    public static final int PENDING_ACTIVATION = 2;

    /**
     * 判断给定状态是否允许登录。
     * <p>
     * 仅 {@link #ACTIVE} 和 {@link #PENDING_ACTIVATION} 状态允许登录。
     * 待激活用户登录后应引导其完成激活流程。
     * </p>
     *
     * @param status 用户账号状态值，可能为 null
     * @return {@code true} 表示允许登录
     */
    public static boolean canLogin(Integer status) {
        return status != null && (status == ACTIVE || status == PENDING_ACTIVATION);
    }

    /**
     * 判断给定状态是否为待激活。
     *
     * @param status 用户账号状态值，可能为 null
     * @return {@code true} 表示处于待激活状态
     */
    public static boolean isPendingActivation(Integer status) {
        return status != null && status == PENDING_ACTIVATION;
    }
}
