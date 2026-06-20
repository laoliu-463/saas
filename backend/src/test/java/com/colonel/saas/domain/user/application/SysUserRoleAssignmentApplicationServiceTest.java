package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserPermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserRoleAssignmentApplicationServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private UserPermissionCacheService userPermissionCacheService;
    @Mock private UserAccessPolicy userAccessPolicy;

    private SysUserRoleAssignmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SysUserRoleAssignmentApplicationService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
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
        SysUser target = user(userId, "alice", 0);

        when(sysUserMapper.selectById(userId)).thenReturn(target);
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(
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
        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        ArgumentCaptor<SysUserRole> inserted = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper, org.mockito.Mockito.times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(SysUserRole::getUserId)
                .containsExactly(userId, userId);
        assertThat(inserted.getAllValues()).extracting(SysUserRole::getRoleId)
                .containsExactly(roleA, roleB);
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

        when(sysUserMapper.selectById(userId)).thenReturn(user(userId, "bob", 0));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(role(adminRoleId, RoleCodes.ADMIN, 1)));
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of());
        SysUserRole existingAdminRelation = new SysUserRole();
        existingAdminRelation.setUserId(existingAdminId);
        existingAdminRelation.setRoleId(adminRoleId);
        when(sysUserRoleMapper.findByRoleId(adminRoleId)).thenReturn(List.of(existingAdminRelation));
        when(sysUserMapper.selectBatchIds(List.of(existingAdminId))).thenReturn(List.of(user(existingAdminId, "admin", 0)));

        assertThatThrownBy(() -> service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of(adminRoleId)),
                currentUserId,
                DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号已存在");

        verify(sysUserRoleMapper, never()).deleteByUserIdPhysical(any());
        verify(userPermissionCacheService, never()).invalidateUser(any());
    }

    @Test
    void assignRoles_targetAlreadyAdmin_shouldAllowAdminRole() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();
        SysUserRole currentAdminRelation = new SysUserRole();
        currentAdminRelation.setUserId(userId);
        currentAdminRelation.setRoleId(adminRoleId);

        when(sysUserMapper.selectById(userId)).thenReturn(user(userId, "admin", 0));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(role(adminRoleId, RoleCodes.ADMIN, 1)));
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of(currentAdminRelation));

        service.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of(adminRoleId)),
                currentUserId,
                DataScope.ALL);

        verify(sysUserRoleMapper, never()).findByRoleId(adminRoleId);
        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateRole(adminRoleId);
    }

    private static SysUser user(UUID id, String username, Integer deleted) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDeptId(UUID.randomUUID());
        user.setDeleted(deleted);
        return user;
    }

    private static SysRole role(UUID id, String roleCode, Integer status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setStatus(status);
        return role;
    }
}
