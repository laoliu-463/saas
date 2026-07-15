package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore.RoleAssignableRole;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore.RoleAssignableUser;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserPermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserRoleAssignmentApplicationServiceTest {

    @Mock private UserRoleAssignmentStore roleAssignmentStore;
    @Mock private OperationLogService operationLogService;
    @Mock private UserPermissionCacheService userPermissionCacheService;
    @Mock private UserAccessPolicy userAccessPolicy;

    private SysUserRoleAssignmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SysUserRoleAssignmentApplicationService(
                roleAssignmentStore,
                operationLogService,
                userPermissionCacheService,
                userAccessPolicy);
    }

    @Test
    void assignRoles_replacesDistinctRolesInvalidatesCacheAndRecordsAudit() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();

        when(roleAssignmentStore.findUser(userId)).thenReturn(Optional.of(user(userId, "alice", 0)));
        when(roleAssignmentStore.findRolesByIds(any())).thenReturn(List.of(
                role(roleA, RoleCodes.BIZ_LEADER, 1),
                role(roleB, RoleCodes.BIZ_STAFF, 1)));

        service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(Arrays.asList(roleA, null, roleB, roleA)),
                currentUserId,
                DataScope.ALL);

        verify(userAccessPolicy).assertCanAccess(
                any(UserAccessPolicy.AccessibleUser.class),
                eq(currentUserId),
                eq(DataScope.ALL));
        verify(roleAssignmentStore).replaceUserRoles(userId, List.of(roleA, roleB));
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateRole(roleA);
        verify(userPermissionCacheService).invalidateRole(roleB);
        verify(operationLogService).recordSystemAction(
                currentUserId,
                "用户管理",
                "分配角色",
                "PUT",
                "SysUser",
                userId.toString(),
                "alice",
                "更新用户角色: alice");
    }

    @Test
    void assignRoles_adminRoleForSecondUser_shouldThrowDuplicate() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();
        UUID existingAdminId = UUID.randomUUID();

        when(roleAssignmentStore.findUser(userId)).thenReturn(Optional.of(user(userId, "bob", 0)));
        when(roleAssignmentStore.findRolesByIds(any())).thenReturn(List.of(role(adminRoleId, RoleCodes.ADMIN, 1)));
        when(roleAssignmentStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(roleAssignmentStore.findUserIdsByRoleId(adminRoleId)).thenReturn(List.of(existingAdminId));
        when(roleAssignmentStore.findUsersByIds(List.of(existingAdminId)))
                .thenReturn(List.of(user(existingAdminId, "admin", 0)));

        assertThatThrownBy(() -> service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of(adminRoleId)),
                currentUserId,
                DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号已存在");

        verify(roleAssignmentStore, never()).replaceUserRoles(any(), any());
        verify(userPermissionCacheService, never()).invalidateUser(any());
    }

    @Test
    void assignRoles_targetAlreadyAdmin_shouldAllowAdminRole() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();

        when(roleAssignmentStore.findUser(userId)).thenReturn(Optional.of(user(userId, "admin", 0)));
        when(roleAssignmentStore.findRolesByIds(any())).thenReturn(List.of(role(adminRoleId, RoleCodes.ADMIN, 1)));
        when(roleAssignmentStore.findRoleIdsByUserId(userId)).thenReturn(List.of(adminRoleId));

        service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of(adminRoleId)),
                currentUserId,
                DataScope.ALL);

        verify(roleAssignmentStore, never()).findUserIdsByRoleId(adminRoleId);
        verify(roleAssignmentStore).replaceUserRoles(userId, List.of(adminRoleId));
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateRole(adminRoleId);
    }

    @Test
    void assignRoles_softDeletedUser_shouldThrowNotFound() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        when(roleAssignmentStore.findUser(userId)).thenReturn(Optional.of(user(userId, "deleted-user", 1)));

        assertThatThrownBy(() -> service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of()),
                currentUserId,
                DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");

        verify(roleAssignmentStore, never()).replaceUserRoles(any(), any());
    }

    private static RoleAssignableUser user(UUID id, String username, Integer deleted) {
        return new RoleAssignableUser(id, username, UUID.randomUUID(), deleted);
    }

    private static RoleAssignableRole role(UUID id, String roleCode, Integer status) {
        return new RoleAssignableRole(id, roleCode, status);
    }
}
