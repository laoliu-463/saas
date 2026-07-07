package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private UserDomainService userDomainService;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userDomainService = new UserDomainService(
                sysUserMapper,
                sysRoleMapper,
                passwordEncoder,
                operationLogService,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
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

        CurrentUserResponse response = userDomainService.getCurrentUser(
                userId,
                deptId,
                DataScope.DEPT,
                List.of(RoleCodes.CHANNEL_LEADER)
        );

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

        assertThatThrownBy(() -> userDomainService.changePassword(
                userId,
                new ChangePasswordRequest("wrong", "new-pass-123")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码错误");

        verify(sysUserMapper, never()).updateById(any());
    }

    @Test
    void changePassword_shouldEncodeAndPersistNewPassword() {
        SysUser user = user("channel_staff", "渠道", deptId, "$2a$encoded-old");
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("old-pass", "$2a$encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("$2a$encoded-new");

        userDomainService.changePassword(userId, new ChangePasswordRequest("old-pass", "new-pass-123"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded-new");
        assertThat(captor.getValue().getForcePasswordChange()).isFalse();
        verify(operationLogService).recordSystemAction(
                userId,
                "用户域",
                "修改密码",
                "PUT",
                "SysUser",
                userId.toString(),
                "channel_staff",
                "用户修改自己的登录密码"
        );
    }

    @Test
    void changePassword_shouldActivatePendingUserAfterFirstChange() {
        SysUser user = user("pending", "待激活", deptId, "$2a$encoded-old");
        user.setStatus(2);
        user.setForcePasswordChange(true);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("old-pass", "$2a$encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("$2a$encoded-new");

        userDomainService.changePassword(userId, new ChangePasswordRequest("old-pass", "new-pass-123"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getForcePasswordChange()).isFalse();
    }

    @Test
    void getUserDataScope_shouldResolveSelfGroupAndAll() {
        SysUser member = user("member", "组员", deptId, "$2a$member");
        member.setId(UUID.randomUUID());
        when(sysUserMapper.selectList(any())).thenReturn(List.of(member));

        UserDataScopeResponse self = userDomainService.getUserDataScope(userId, deptId, DataScope.PERSONAL);
        UserDataScopeResponse group = userDomainService.getUserDataScope(userId, deptId, DataScope.DEPT);
        UserDataScopeResponse all = userDomainService.getUserDataScope(userId, deptId, DataScope.ALL);

        assertThat(self.scope()).isEqualTo("self");
        assertThat(self.userIds()).containsExactly(userId);
        assertThat(group.scope()).isEqualTo("group");
        assertThat(group.userIds()).containsExactly(member.getId());
        assertThat(all.scope()).isEqualTo("all");
        assertThat(all.userIds()).isEmpty();
    }

    @Test
    void checkPermission_shouldAllowAdminAndConfiguredRoleOperations() {
        CheckPermissionResponse admin = userDomainService.checkPermission(
                userId,
                List.of(RoleCodes.ADMIN),
                new CheckPermissionRequest("anything", "delete")
        );
        assertThat(admin.allowed()).isTrue();

        SysRole role = role(RoleCodes.BIZ_STAFF, 1, Map.of(
                "operations", Map.of("product", List.of("audit", "pin"))
        ));
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role));

        CheckPermissionResponse audit = userDomainService.checkPermission(
                userId,
                List.of(RoleCodes.BIZ_STAFF),
                new CheckPermissionRequest("product", "audit")
        );
        CheckPermissionResponse export = userDomainService.checkPermission(
                userId,
                List.of(RoleCodes.BIZ_STAFF),
                new CheckPermissionRequest("product", "export")
        );

        assertThat(audit.allowed()).isTrue();
        assertThat(export.allowed()).isFalse();
    }

    private SysUser user(String username, String realName, UUID deptId, String password) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername(username);
        user.setRealName(realName);
        user.setDeptId(deptId);
        user.setPassword(password);
        user.setStatus(1);
        return user;
    }

    private SysRole role(String roleCode, Integer dataScope, Map<String, Object> permissions) {
        SysRole role = new SysRole();
        role.setId(UUID.randomUUID());
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setDataScope(dataScope);
        role.setStatus(1);
        role.setPermissions(permissions);
        return role;
    }
}
