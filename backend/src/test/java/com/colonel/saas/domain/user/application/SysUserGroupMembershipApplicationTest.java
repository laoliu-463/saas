package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SysUserGroupMembershipApplicationTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private UserPermissionCacheService userPermissionCacheService;
    @Mock
    private OrgStructureService orgStructureService;

    private SysUserGroupMembershipApplication application;

    @BeforeEach
    void setUp() {
        application = new SysUserGroupMembershipApplication(
                sysUserMapper,
                operationLogService,
                userDomainEventPublisher,
                userPermissionCacheService,
                orgStructureService);
    }

    @Test
    void assignUsersToGroup_updatesDeptAndPublishesOrgChange() {
        UUID groupId = UUID.randomUUID();
        UUID effectiveDeptId = UUID.randomUUID();
        UUID parentDeptId = UUID.randomUUID();
        UUID oldDeptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SysUser user = user(userId, oldDeptId);

        when(orgStructureService.resolveAssignment(null, groupId))
                .thenReturn(new OrgStructureService.ResolvedAssignment(effectiveDeptId, parentDeptId, groupId));
        when(sysUserMapper.findActiveById(userId)).thenReturn(java.util.Optional.of(user));
        when(orgStructureService.splitAssignment(oldDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(oldDeptId, null, "old", null, "department"));
        when(orgStructureService.splitAssignment(effectiveDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(parentDeptId, groupId, "parent", "group", "biz"));
        when(orgStructureService.formatOrgChangeRemark(userId, oldDeptId, effectiveDeptId, currentUserId))
                .thenReturn("org changed");

        application.assignUsersToGroup(groupId, List.of(userId), currentUserId);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeptId()).isEqualTo(effectiveDeptId);
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("组织归属变更"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("org changed"));
        verify(userDomainEventPublisher).publishUserGroupChanged(
                eq(userId), eq(null), eq(groupId), eq(oldDeptId), eq(parentDeptId), eq(currentUserId));
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(oldDeptId, effectiveDeptId);
    }

    @Test
    void removeUsersFromGroup_matchingDept_clearsDeptAndPublishesOrgChange() {
        UUID groupId = UUID.randomUUID();
        UUID parentDeptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SysUser user = user(userId, groupId);

        when(sysUserMapper.findActiveById(userId)).thenReturn(java.util.Optional.of(user));
        when(orgStructureService.splitAssignment(groupId))
                .thenReturn(new OrgStructureService.SplitAssignment(parentDeptId, groupId, "parent", "group", "biz"));
        when(orgStructureService.splitAssignment(null))
                .thenReturn(new OrgStructureService.SplitAssignment(null, null, null, null, null));
        when(orgStructureService.formatOrgChangeRemark(userId, groupId, null, currentUserId))
                .thenReturn("removed");

        application.removeUsersFromGroup(groupId, List.of(userId), currentUserId);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeptId()).isNull();
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("组织归属变更"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("removed"));
        verify(userDomainEventPublisher).publishUserGroupChanged(
                eq(userId), eq(groupId), eq(null), eq(parentDeptId), eq(null), eq(currentUserId));
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(groupId, null);
    }

    @Test
    void removeUsersFromGroup_nonMatchingDept_shouldSkipMutation() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SysUser user = user(userId, UUID.randomUUID());

        when(sysUserMapper.findActiveById(userId)).thenReturn(java.util.Optional.of(user));

        application.removeUsersFromGroup(groupId, List.of(userId), currentUserId);

        verify(sysUserMapper, never()).updateById(any());
        verify(operationLogService, never()).recordSystemAction(any(), any(), any(), any(), any(), any(), any(), any());
        verify(userDomainEventPublisher, never()).publishUserGroupChanged(any(), any(), any(), any(), any(), any());
        verify(userPermissionCacheService, never()).invalidateUser(any());
        verify(userPermissionCacheService, never()).invalidateDataScopeForGroupChange(any(), any());
    }

    @Test
    void assignUsersToGroup_missingUser_shouldThrow() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(orgStructureService.resolveAssignment(null, groupId))
                .thenReturn(new OrgStructureService.ResolvedAssignment(groupId, null, groupId));
        when(sysUserMapper.findActiveById(userId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> application.assignUsersToGroup(groupId, List.of(userId), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");

        verify(sysUserMapper, never()).updateById(any());
    }

    private static SysUser user(UUID id, UUID deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("alice");
        user.setDeptId(deptId);
        return user;
    }
}
