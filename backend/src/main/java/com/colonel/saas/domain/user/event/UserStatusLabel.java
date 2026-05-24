package com.colonel.saas.domain.user.event;

import com.colonel.saas.constant.SysUserStatus;

public final class UserStatusLabel {

    private UserStatusLabel() {
    }

    public static String fromCode(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        if (status == SysUserStatus.DISABLED) {
            return "DISABLED";
        }
        if (status == SysUserStatus.PENDING_ACTIVATION) {
            return "PENDING_ACTIVATION";
        }
        if (status == SysUserStatus.ACTIVE) {
            return "ACTIVE";
        }
        return "UNKNOWN";
    }
}
