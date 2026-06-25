package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.CredentialUser;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.PasswordChangeUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCredentialPolicyTest {

    private UserCredentialPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserCredentialPolicy();
    }

    @Test
    void assertOldPasswordMatched_shouldRejectMismatch() {
        assertThatThrownBy(() -> policy.assertOldPasswordMatched(false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("原密码错误");
    }

    @Test
    void buildPasswordChangeUpdate_shouldActivatePendingUserAndClearForceFlag() {
        UUID userId = UUID.randomUUID();
        CredentialUser current = user(SysUserStatus.PENDING_ACTIVATION, "pending");

        PasswordChangeUpdate update = policy.buildPasswordChangeUpdate(userId, current, "$2a$new");

        assertThat(update.userId()).isEqualTo(userId);
        assertThat(update.encodedPassword()).isEqualTo("$2a$new");
        assertThat(update.status()).isEqualTo(SysUserStatus.ACTIVE);
        assertThat(update.forcePasswordChange()).isFalse();
    }

    @Test
    void buildPasswordChangeUpdate_shouldKeepActiveStatusAndClearForceFlag() {
        UUID userId = UUID.randomUUID();
        CredentialUser current = user(SysUserStatus.ACTIVE, "channel_staff");

        PasswordChangeUpdate update = policy.buildPasswordChangeUpdate(userId, current, "$2a$new");

        assertThat(update.status()).isEqualTo(SysUserStatus.ACTIVE);
        assertThat(update.forcePasswordChange()).isFalse();
    }

    @Test
    void passwordChangeAudit_shouldDescribeSelfServicePasswordChange() {
        UUID userId = UUID.randomUUID();
        CredentialUser current = user(SysUserStatus.ACTIVE, "channel_staff");

        UserCredentialPolicy.PasswordChangeAudit audit = policy.passwordChangeAudit(userId, current);

        assertThat(audit.userId()).isEqualTo(userId);
        assertThat(audit.domain()).isEqualTo("用户域");
        assertThat(audit.action()).isEqualTo("修改密码");
        assertThat(audit.method()).isEqualTo("PUT");
        assertThat(audit.entityType()).isEqualTo("SysUser");
        assertThat(audit.entityId()).isEqualTo(userId.toString());
        assertThat(audit.entityName()).isEqualTo("channel_staff");
        assertThat(audit.description()).isEqualTo("用户修改自己的登录密码");
    }

    @Test
    void policy_shouldNotDependOnPersistenceEntity() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/UserCredentialPolicy.java"));

        assertThat(source).doesNotContain("com.colonel.saas.entity.SysUser");
    }

    private static CredentialUser user(Integer status, String username) {
        return new CredentialUser(status, username);
    }
}
