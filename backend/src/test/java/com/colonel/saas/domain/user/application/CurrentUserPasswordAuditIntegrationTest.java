package com.colonel.saas.domain.user.application;

import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DockerAvailable
class CurrentUserPasswordAuditIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CurrentUserApplicationService currentUserApplicationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void changePassword_shouldPersistPasswordStateAndOperationLog() {
        UUID userId = UUID.randomUUID();
        String oldHash = passwordEncoder.encode("old-pass");
        jdbcTemplate.update("""
                        INSERT INTO sys_user (
                            id, username, password, real_name, channel_code,
                            status, force_password_change, deleted
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                userId,
                "audit_user",
                oldHash,
                "审计用户",
                "audit001",
                SysUserStatus.PENDING_ACTIVATION,
                true);

        currentUserApplicationService.changePassword(
                userId,
                new ChangePasswordRequest("old-pass", "new-pass-123"));

        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT password, status, force_password_change FROM sys_user WHERE id = ?",
                userId);
        assertThat(passwordEncoder.matches("new-pass-123", (String) userRow.get("password"))).isTrue();
        assertThat(((Number) userRow.get("status")).intValue()).isEqualTo(SysUserStatus.ACTIVE);
        assertThat(userRow.get("force_password_change")).isEqualTo(false);

        Map<String, Object> logRow = jdbcTemplate.queryForMap("""
                        SELECT user_id, username, module, action, request_method,
                               target_type, target_id, target_name, content
                        FROM operation_log
                        WHERE user_id = ? AND module = '用户域' AND action = '修改密码'
                        ORDER BY create_time DESC
                        LIMIT 1
                        """,
                userId);
        assertThat(logRow.get("user_id")).isEqualTo(userId);
        assertThat(logRow.get("username")).isEqualTo("audit_user");
        assertThat(logRow.get("module")).isEqualTo("用户域");
        assertThat(logRow.get("action")).isEqualTo("修改密码");
        assertThat(logRow.get("request_method")).isEqualTo("PUT");
        assertThat(logRow.get("target_type")).isEqualTo("SysUser");
        assertThat(logRow.get("target_id")).isEqualTo(userId.toString());
        assertThat(logRow.get("target_name")).isEqualTo("audit_user");
        assertThat(logRow.get("content")).isEqualTo("用户修改自己的登录密码");
    }
}
