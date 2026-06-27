package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.application.CurrentUserApplicationService;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户域核心服务测试（迁移到 DDD Application）。
 *
 * <p>DDD-COMPLETE-100-USER-06：测试对象从 UserDomainService
 * 迁移到 CurrentUserApplicationService。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;

    private CurrentUserApplicationService applicationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicationService = new CurrentUserApplicationService(
                sysUserMapper,
                sysRoleMapper,
                passwordEncoder,
                operationLogService,
                new CurrentUserPermissionPolicy(),
                new UserCredentialPolicy());
    }

    @Test
    void getCurrentUser_shouldReturnFreshRoleCodesPermissionsAndScope() {
        SysUser user = user("channel_leader", "渠道组长", deptId, "$2a$encoded");
        SysRole role = role(RoleCodes.CHANNEL_LEADER, 2, Map.of(
                "operations", Map.of("talent", List.of("claim", "tag")),
                "menus", List.of("talent_crm")
        ));
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role));

        CurrentUserResponse response = applicationService.getCurrentUser(
                userId, deptId, DataScope.DEPT, List.of(RoleCodes.CHANNEL_LEADER));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("channel_leader");
        assertThat(response.dataScope()).isEqualTo(2);
        assertThat(response.dataScopeName()).isEqualTo("group");
        assertThat(response.roleCodes()).containsExactly(RoleCodes.CHANNEL_LEADER);
        assertThat(((Map<?, ?>) response.permissions().get("operations")).get("talent"))
                .isEqualTo(List.of("claim", "tag"));
    }

    @Test
    void changePassword_shouldRequireOldPasswordBeforeUpdating() {
        SysUser user = user("channel_staff", "渠道", deptId, "$2a$encoded-old");
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> applicationService.changePassword(
                userId, new ChangePasswordRequest("wrong", "new-pass-123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码错误");

        verify(sysUserMapper, never()).updateById(any());
    }

    @Test
    void changePassword_shouldEncodeAndPersistNewPassword() {
        SysUser user = user("channel_staff", "渠道", deptId, "$2a$encoded-old");
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("old-pass", "$2a$encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("$2a$encoded-new");

        applicationService.changePassword(userId, new ChangePasswordRequest("old-pass", "new-pass-123"));

        verify(sysUserMapper).updateById(any(SysUser.class));
        verify(operationLogService).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getCurrentUser_shouldRejectUnknownUser() {
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.getCurrentUser(
                userId, deptId, DataScope.ALL, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
        void getUserDataScope_shouldReturnAllEmptyForAdmin() {
            UserDataScopeResponse response = applicationService.getUserDataScope(userId, deptId, DataScope.ALL);
            assertThat(response.scope()).isEqualTo("all");
            assertThat(response.code()).isEqualTo(DataScope.ALL.getCode());
            assertThat(response.userIds()).isEmpty();
        }

        @Test
        void getUserDataScope_shouldReturnDeptMembersForGroupScope() {
            SysUser u1 = new SysUser();
            u1.setId(UUID.randomUUID());
            u1.setUsername("u1");
            u1.setDeptId(deptId);
            u1.setStatus(SysUserStatus.ACTIVE);
            u1.setDeleted(0);
            SysUser u2 = new SysUser();
            u2.setId(UUID.randomUUID());
            u2.setUsername("u2");
            u2.setDeptId(deptId);
            u2.setStatus(SysUserStatus.ACTIVE);
            u2.setDeleted(0);
            when(sysUserMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(u1, u2));

            UserDataScopeResponse response = applicationService.getUserDataScope(userId, deptId, DataScope.DEPT);
            assertThat(response.scope()).isEqualTo("group");
            assertThat(response.userIds()).containsExactlyInAnyOrder(u1.getId(), u2.getId());
        }

        @Test
        void getUserDataScope_shouldReturnSelfOnlyForPersonalScope() {
            UserDataScopeResponse response = applicationService.getUserDataScope(userId, deptId, DataScope.PERSONAL);
            assertThat(response.scope()).isEqualTo("self");
            assertThat(response.userIds()).containsExactly(userId);
        }

        @Test
        void checkPermission_shouldDelegateToPolicy() {
            SysUser user = user("admin", "管理员", deptId, "$2a$e");
            org.mockito.Mockito.lenient().when(sysUserMapper.selectById(userId)).thenReturn(user);
            org.mockito.Mockito.lenient().when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role(RoleCodes.ADMIN, 1, Map.of())));

            CheckPermissionResponse response = applicationService.checkPermission(
                    userId,
                    List.of(RoleCodes.ADMIN),
                    new CheckPermissionRequest("talent", "claim"));

            assertThat(response.allowed()).isTrue();
        }

    // ========================== Helper ==========================

    private SysUser user(String username, String realName, UUID deptId, String password) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setUsername(username);
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setPassword(password);
        u.setStatus(SysUserStatus.ACTIVE);
        u.setDeleted(0);
        return u;
    }

    private SysRole role(String code, int dataScope, Map<String, Object> permissions) {
        SysRole r = new SysRole();
        r.setId(UUID.randomUUID());
        r.setRoleCode(code);
        r.setStatus(1);
        r.setDataScope(dataScope);
        r.setPermissions(permissions);
        return r;
    }
}