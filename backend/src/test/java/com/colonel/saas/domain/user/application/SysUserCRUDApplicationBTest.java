package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserCRUDApplicationBTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private UserPermissionCacheService userPermissionCacheService;
    @Mock
    private OrgStructureService orgStructureService;
    @Mock
    private UserAccessPolicy userAccessPolicy;

    private SysUserCRUDApplicationB applicationB;

    @BeforeEach
    void setUp() {
        applicationB = new SysUserCRUDApplicationB(
                sysUserMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                userPermissionCacheService,
                orgStructureService,
                userAccessPolicy);
    }

    @Test
    void update_changesDeptAndDisablesUser_publishesEventsAndInvalidatesCache() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID oldDeptId = UUID.randomUUID();
        UUID parentDeptId = UUID.randomUUID();
        UUID newGroupId = UUID.randomUUID();
        UUID newDeptId = UUID.randomUUID();
        SysUser user = activeUser(userId, oldDeptId);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(orgStructureService.resolveAssignment(parentDeptId, newGroupId))
                .thenReturn(new OrgStructureService.ResolvedAssignment(newDeptId, parentDeptId, newGroupId));
        when(orgStructureService.splitAssignment(oldDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(oldDeptId, null, "old-parent", null, "department"));
        when(orgStructureService.splitAssignment(newDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(parentDeptId, newGroupId, "new-parent", "new-group", "biz"));
        when(orgStructureService.formatOrgChangeRemark(userId, oldDeptId, newDeptId, currentUserId))
                .thenReturn("org changed");
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "更新姓名", "13800000000", "u@x.com",
                SysUserStatus.DISABLED, parentDeptId, newGroupId, null);

        SysUserVO result = applicationB.update(userId, request, currentUserId, DataScope.ALL);

        assertThat(result.getRealName()).isEqualTo("更新姓名");
        assertThat(result.getDeptId()).isEqualTo(newDeptId);
        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        verify(sysUserMapper).updateById(user);
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("组织归属变更"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("org changed"));
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("更新用户"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("更新用户: alice"));
        verify(userDomainEventPublisher).publishUserGroupChanged(
                eq(userId), eq(null), eq(newGroupId), eq(oldDeptId), eq(parentDeptId), eq(currentUserId));
        verify(userDomainEventPublisher).publishUserDisabled(
                eq(userId), eq(SysUserStatus.ACTIVE), eq(SysUserStatus.DISABLED), eq(currentUserId));
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(oldDeptId, newDeptId);
    }

    @Test
    void update_withoutDeptChange_doesNotPublishOrgChange() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUser user = activeUser(userId, deptId);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "Alice 2", "13900000000", "a2@x.com", SysUserStatus.ACTIVE, null, null, null);

        applicationB.update(userId, request, currentUserId, DataScope.ALL);

        verify(orgStructureService, never()).splitAssignment(any());
        verify(userDomainEventPublisher, never()).publishUserGroupChanged(any(), any(), any(), any(), any(), any());
        verify(userDomainEventPublisher, never()).publishUserDisabled(any(), any(), any(), any());
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(deptId, deptId);
    }

    @Test
    void delete_self_shouldThrowStateInvalid() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> applicationB.delete(userId, userId, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(ResultCode.STATE_INVALID.getCode());

        verify(sysUserMapper, never()).selectById(any());
        verify(sysUserMapper, never()).softDeleteById(any());
    }

    @Test
    void delete_normalCase_softDeletesAndInvalidatesCache() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SysUser user = activeUser(userId, UUID.randomUUID());

        when(sysUserMapper.selectById(userId)).thenReturn(user);

        applicationB.delete(userId, currentUserId, DataScope.ALL);

        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        verify(sysUserMapper).softDeleteById(userId);
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("删除用户"), eq("DELETE"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("删除用户: alice"));
    }

    @Test
    void resetPassword_updatesEncodedPasswordAndForcesPasswordChange() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SysUser user = activeUser(userId, UUID.randomUUID());

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("encoded");

        applicationB.resetPassword(
                userId,
                new SysUserResetPasswordRequest("NewPassw0rd!"),
                currentUserId,
                DataScope.ALL);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().getForcePasswordChange()).isTrue();
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("重置密码"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("重置用户密码: alice"));
    }

    private static SysUser activeUser(UUID id, UUID deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("alice");
        user.setRealName("Alice");
        user.setDeptId(deptId);
        user.setStatus(SysUserStatus.ACTIVE);
        return user;
    }
}
