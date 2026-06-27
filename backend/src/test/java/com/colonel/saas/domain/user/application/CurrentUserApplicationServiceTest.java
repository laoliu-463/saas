package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserApplicationServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private CurrentUserPermissionPolicy currentUserPermissionPolicy;
    @Mock
    private UserCredentialPolicy userCredentialPolicy;

    private CurrentUserApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CurrentUserApplicationService(
                sysUserMapper,
                sysRoleMapper,
                passwordEncoder,
                operationLogService,
                currentUserPermissionPolicy,
                userCredentialPolicy
        );
    }

    @Test
    void currentUser_shouldResolveUserFromMapperAndPolicy() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("test");
        user.setRealName("Test User");
        user.setDeptId(deptId);
        user.setStatus(1);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());

        List<String> resolvedRoles = List.of("ROLE_USER");
        when(currentUserPermissionPolicy.resolveRoleCodes(any(), any())).thenReturn(resolvedRoles);
        when(currentUserPermissionPolicy.resolveDataScopeCode(any(), any(), any())).thenReturn(1);
        when(currentUserPermissionPolicy.scopeName(1)).thenReturn("self");
        when(currentUserPermissionPolicy.mergePermissions(any(), anyInt())).thenReturn(Map.of());

        CurrentUserResponse response = applicationService.currentUser(userId, deptId, DataScope.PERSONAL, null);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("test");
        assertThat(response.roleCodes()).contains("ROLE_USER");
    }

    @Test
    void checkPermission_shouldDelegateToPolicy() {
        UUID userId = UUID.randomUUID();
        CheckPermissionRequest request = new CheckPermissionRequest("product", "audit");
        CheckPermissionResponse response = new CheckPermissionResponse("product", "audit", false);

        when(sysRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(currentUserPermissionPolicy.checkPermission(any(), any(), any())).thenReturn(response);

        assertThat(applicationService.checkPermission(userId, null, request)).isSameAs(response);
    }

    @Test
    void changePassword_shouldVerifyEncoderAndPasswordPolicy() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("old-pass", "new-pass-123");

        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("test");
        user.setStatus(1);
        user.setPassword("hashed-old-pass");

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("old-pass", "hashed-old-pass")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("hashed-new-pass");

        UserCredentialPolicy.CredentialUser credentialUser =
                new UserCredentialPolicy.CredentialUser(1, user.getUsername());
        UserCredentialPolicy.PasswordChangeUpdate update =
                new UserCredentialPolicy.PasswordChangeUpdate(userId, "hashed-new-pass", 1, false);
        when(userCredentialPolicy.buildPasswordChangeUpdate(any(), any(), any())).thenReturn(update);

        UserCredentialPolicy.PasswordChangeAudit audit = new UserCredentialPolicy.PasswordChangeAudit(
                userId, "User", "ChangePassword", "POST", "User", userId.toString(), "test", "description"
        );
        when(userCredentialPolicy.passwordChangeAudit(any(), any())).thenReturn(audit);

        applicationService.changePassword(userId, request);

        verify(sysUserMapper).updateById(any());
        verify(operationLogService).recordSystemAction(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void dataScope_shouldCalculatePersonalCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        UserDataScopeResponse response = applicationService.dataScope(userId, deptId, DataScope.PERSONAL);
        assertThat(response.scope()).isEqualTo("self");
        assertThat(response.userIds()).containsExactly(userId);
    }
}
