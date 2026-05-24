package com.colonel.saas.constant;

/**
 * 用户账号状态：待激活 → 正常 → 已禁用。
 */
public final class SysUserStatus {

    public static final int DISABLED = 0;
    public static final int ACTIVE = 1;
    public static final int PENDING_ACTIVATION = 2;

    private SysUserStatus() {
    }

    public static boolean canLogin(Integer status) {
        return status != null && (status == ACTIVE || status == PENDING_ACTIVATION);
    }

    public static boolean isPendingActivation(Integer status) {
        return status != null && status == PENDING_ACTIVATION;
    }
}
