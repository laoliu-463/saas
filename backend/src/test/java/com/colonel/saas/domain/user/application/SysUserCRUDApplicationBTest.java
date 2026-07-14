package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.port.UserCrudMutationStore;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedUser;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserCRUDApplicationBTest {

    @Mock
    private UserCrudMutationStore userStore;
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
    @Mock
    private AuthorizationVersionApplicationService authorizationVersionService;

    private SysUserCRUDApplicationB applicationB;

    @BeforeEach
    void setUp() {
        applicationB = new SysUserCRUDApplicationB(
                userStore,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                userPermissionCacheService,
                orgStructureService,
                userAccessPolicy,
                authorizationVersionService);
    }

    @Test
    void update_changesDeptAndDisablesUser_publishesEventsAndInvalidatesCache() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID oldDeptId = UUID.randomUUID();
        UUID parentDeptId = UUID.randomUUID();
        UUID newGroupId = UUID.randomUUID();
        UUID newDeptId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, oldDeptId);

        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(orgStructureService.resolveAssignment(parentDeptId, newGroupId))
                .thenReturn(new OrgStructureService.ResolvedAssignment(newDeptId, parentDeptId, newGroupId));
        when(orgStructureService.splitAssignment(oldDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(oldDeptId, null, "old-parent", null, "department"));
        when(orgStructureService.splitAssignment(newDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(parentDeptId, newGroupId, "new-parent", "new-group", "biz"));
        when(orgStructureService.formatOrgChangeRemark(userId, oldDeptId, newDeptId, currentUserId))
                .thenReturn("org changed");
        when(userStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "更新姓名", "13800000000", "u@x.com",
                SysUserStatus.DISABLED, parentDeptId, newGroupId, null);

        SysUserVO result = applicationB.update(userId, request, currentUserId, DataScope.ALL);

        assertThat(result.getRealName()).isEqualTo("更新姓名");
        assertThat(result.getDeptId()).isEqualTo(newDeptId);
        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        ArgumentCaptor<ManagedUser> saved = ArgumentCaptor.forClass(ManagedUser.class);
        verify(userStore).saveUser(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(userId);
        assertThat(saved.getValue().realName()).isEqualTo("更新姓名");
        assertThat(saved.getValue().phone()).isEqualTo("13800000000");
        assertThat(saved.getValue().email()).isEqualTo("u@x.com");
        assertThat(saved.getValue().deptId()).isEqualTo(newDeptId);
        assertThat(saved.getValue().status()).isEqualTo(SysUserStatus.DISABLED);
        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).saveUser(any(ManagedUser.class));
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                currentUserId);
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
        ManagedUser user = activeUser(userId, deptId);

        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(userStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "Alice 2", "13900000000", "a2@x.com", SysUserStatus.ACTIVE, null, null, null);

        applicationB.update(userId, request, currentUserId, DataScope.ALL);

        verify(orgStructureService, never()).splitAssignment(any());
        verify(userDomainEventPublisher, never()).publishUserGroupChanged(any(), any(), any(), any(), any(), any());
        verify(userDomainEventPublisher, never()).publishUserDisabled(any(), any(), any(), any());
        verify(authorizationVersionService, never()).incrementUser(any(), any(), any());
        verify(userPermissionCacheService).invalidateDataScopeForGroupChange(deptId, deptId);
    }

    @Test
    void update_statusOnlyChange_advancesAuthorizationVersion() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, deptId);
        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(userStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationB.update(
                userId,
                new SysUserUpdateRequest(
                        "Alice", null, null, SysUserStatus.DISABLED, null, null, null),
                currentUserId,
                DataScope.ALL);

        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).saveUser(any(ManagedUser.class));
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                currentUserId);
    }

    @Test
    void update_deptOnlyChange_advancesAuthorizationVersion() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID oldDeptId = UUID.randomUUID();
        UUID newDeptId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, oldDeptId);
        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(orgStructureService.splitAssignment(oldDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(oldDeptId, null, "old", null, "department"));
        when(orgStructureService.splitAssignment(newDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(newDeptId, null, "new", null, "department"));
        when(userStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationB.update(
                userId,
                new SysUserUpdateRequest(
                        "Alice", null, null, SysUserStatus.ACTIVE, null, null, newDeptId),
                currentUserId,
                DataScope.ALL);

        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).saveUser(any(ManagedUser.class));
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                currentUserId);
    }

    @Test
    void update_versionFailurePropagatesBeforeOtherSideEffects() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, UUID.randomUUID());
        RuntimeException failure = new RuntimeException("version failed");
        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        doThrow(failure).when(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                currentUserId);

        assertThatThrownBy(() -> applicationB.update(
                userId,
                new SysUserUpdateRequest(
                        "Alice", null, null, SysUserStatus.DISABLED, null, null, null),
                currentUserId,
                DataScope.ALL))
                .isSameAs(failure);

        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).saveUser(any(ManagedUser.class));
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                currentUserId);
        verify(operationLogService, never()).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(userDomainEventPublisher, never()).publishUserDisabled(any(), any(), any(), any());
        verify(userPermissionCacheService, never()).invalidateUser(any());
    }

    @Test
    void delete_self_shouldThrowStateInvalid() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> applicationB.delete(userId, userId, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(ResultCode.STATE_INVALID.getCode());

        verify(userStore, never()).findUser(any());
        verify(userStore, never()).softDeleteUser(any());
    }

    @Test
    void delete_normalCase_softDeletesAndInvalidatesCache() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, UUID.randomUUID());

        when(userStore.findUser(userId)).thenReturn(Optional.of(user));

        applicationB.delete(userId, currentUserId, DataScope.ALL);

        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        verify(userStore).deleteUserRoles(userId);
        verify(userStore).softDeleteUser(userId);
        verify(userPermissionCacheService).invalidateUser(userId);
        verify(authorizationVersionService, never()).incrementUser(any(), any(), any());
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("删除用户"), eq("DELETE"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("删除用户: alice"));
    }

    @Test
    void resetPassword_updatesEncodedPasswordAndForcesPasswordChange() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, UUID.randomUUID());

        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("encoded");

        applicationB.resetPassword(
                userId,
                new SysUserResetPasswordRequest("NewPassw0rd!"),
                currentUserId,
                DataScope.ALL);

        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).updatePassword(userId, "encoded", true);
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_PASSWORD_RESET",
                currentUserId);
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("用户管理"), eq("重置密码"), eq("PUT"),
                eq("SysUser"), eq(userId.toString()), eq("alice"), eq("重置用户密码: alice"));
    }

    @Test
    void resetPassword_versionFailurePropagatesBeforeAudit() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        ManagedUser user = activeUser(userId, UUID.randomUUID());
        RuntimeException failure = new RuntimeException("version failed");
        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("encoded");
        doThrow(failure).when(authorizationVersionService).incrementUser(
                userId,
                "USER_PASSWORD_RESET",
                currentUserId);

        assertThatThrownBy(() -> applicationB.resetPassword(
                userId,
                new SysUserResetPasswordRequest("NewPassw0rd!"),
                currentUserId,
                DataScope.ALL))
                .isSameAs(failure);

        InOrder factThenVersion = inOrder(userStore, authorizationVersionService);
        factThenVersion.verify(userStore).updatePassword(userId, "encoded", true);
        factThenVersion.verify(authorizationVersionService).incrementUser(
                userId,
                "USER_PASSWORD_RESET",
                currentUserId);
        verify(operationLogService, never()).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateAndResetPassword_areTransactionalWithCheckedExceptionRollback() throws NoSuchMethodException {
        Transactional updateTransaction = SysUserCRUDApplicationB.class
                .getDeclaredMethod(
                        "update",
                        UUID.class,
                        SysUserUpdateRequest.class,
                        UUID.class,
                        DataScope.class)
                .getAnnotation(Transactional.class);
        Transactional resetTransaction = SysUserCRUDApplicationB.class
                .getDeclaredMethod(
                        "resetPassword",
                        UUID.class,
                        SysUserResetPasswordRequest.class,
                        UUID.class,
                        DataScope.class)
                .getAnnotation(Transactional.class);

        assertThat(updateTransaction).isNotNull();
        assertThat(updateTransaction.rollbackFor()).containsExactly(Exception.class);
        assertThat(resetTransaction).isNotNull();
        assertThat(resetTransaction.rollbackFor()).containsExactly(Exception.class);
    }

    private static ManagedUser activeUser(UUID id, UUID deptId) {
        return new ManagedUser(
                id,
                "alice",
                "Alice",
                null,
                null,
                deptId,
                SysUserStatus.ACTIVE,
                false,
                null,
                null,
                0);
    }
}
