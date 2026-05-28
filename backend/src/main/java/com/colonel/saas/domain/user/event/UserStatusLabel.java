package com.colonel.saas.domain.user.event;

import com.colonel.saas.constant.SysUserStatus;

/**
 * 用户状态码转标签工具类。
 *
 * <p>将 {@link SysUserStatus} 中定义的整数状态码转换为人类可读的状态标签字符串，
 * 用于领域事件中记录用户状态（事件载荷不适合携带整数状态码）。</p>
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code SysUserStatus.DISABLED} -> {@code "DISABLED"}</li>
 *   <li>{@code SysUserStatus.PENDING_ACTIVATION} -> {@code "PENDING_ACTIVATION"}</li>
 *   <li>{@code SysUserStatus.ACTIVE} -> {@code "ACTIVE"}</li>
 *   <li>其他/未知 -> {@code "UNKNOWN"}</li>
 * </ul>
 */
public final class UserStatusLabel {

    /** 工具类，禁止实例化。 */
    private UserStatusLabel() {
    }

    /**
     * 将用户状态码转换为标签字符串。
     *
     * @param status 用户状态码（对应 {@link SysUserStatus} 常量，可为 null）
     * @return 状态标签字符串，无法识别时返回 {@code "UNKNOWN"}
     */
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
