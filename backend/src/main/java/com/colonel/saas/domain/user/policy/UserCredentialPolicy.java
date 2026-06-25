package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.SysUserStatus;

import java.util.UUID;

/**
 * 用户凭证策略（DDD-USER-MIGRATION-U14）。
 *
 * <p>负责当前用户自助修改密码时的领域规则：
 * 旧密码必须匹配、待激活账号改密后转为正常、强制改密标记清除，
 * 并统一操作审计语义。</p>
 */
public class UserCredentialPolicy {

    public record CredentialUser(Integer status, String username) {
    }

    public record PasswordChangeUpdate(
            UUID userId,
            String encodedPassword,
            Integer status,
            boolean forcePasswordChange) {
    }

    public void assertOldPasswordMatched(boolean oldPasswordMatched) {
        if (!oldPasswordMatched) {
            throw BusinessException.forbidden("原密码错误");
        }
    }

    public PasswordChangeUpdate buildPasswordChangeUpdate(
            UUID userId,
            CredentialUser currentUser,
            String encodedNewPassword) {
        return new PasswordChangeUpdate(
                userId,
                encodedNewPassword,
                SysUserStatus.ACTIVE,
                false
        );
    }

    public PasswordChangeAudit passwordChangeAudit(UUID userId, CredentialUser currentUser) {
        return new PasswordChangeAudit(
                userId,
                "用户域",
                "修改密码",
                "PUT",
                "SysUser",
                userId.toString(),
                currentUser == null ? null : currentUser.username(),
                "用户修改自己的登录密码"
        );
    }

    public record PasswordChangeAudit(
            UUID userId,
            String domain,
            String action,
            String method,
            String entityType,
            String entityId,
            String entityName,
            String description) {
    }
}
