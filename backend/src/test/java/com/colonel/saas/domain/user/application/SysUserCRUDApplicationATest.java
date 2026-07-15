package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserChannelCodePolicy;
import com.colonel.saas.domain.user.port.UserCrudMutationStore;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedRole;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedUser;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.NewUser;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysUserCRUDApplicationA 单元测试（DDD-USER-MIGRATION-012，Issue #21）。
 *
 * <p>覆盖 4 个核心场景：
 * <ul>
 *   <li>getById 正常返回（带数据权限校验 + 组织归属 enrichment）</li>
 *   <li>getById 用户不存在抛 notFound</li>
 *   <li>create 正常创建（用户名校验 + 角色替换 + 渠道编码生成 + 域事件发布）</li>
 *   <li>create 用户名重复抛 duplicate</li>
 * </ul>
 *
 * <p>SysUserService true-route 兼容入口由委托边界测试保证；本类覆盖 CRUD 应用服务行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserCRUDApplicationATest {

    @Mock
    private UserCrudMutationStore userStore;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private OrgStructureService orgStructureService;
    @Mock
    private UserAccessPolicy userAccessPolicy;
    @Mock
    private UserChannelCodePolicy userChannelCodePolicy;

    private SysUserCRUDApplicationA applicationA;

    @BeforeEach
    void setUp() {
        applicationA = new SysUserCRUDApplicationA(
                userStore,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                orgStructureService,
                userAccessPolicy,
                userChannelCodePolicy);
    }

    // ===== getById =====

    @Test
    void getById_normalCase_returnsEnrichedVO() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ManagedUser user = managedUser(userId, "alice", deptId, 1);

        when(userStore.findUser(userId)).thenReturn(Optional.of(user));
        when(userStore.findRoleIdsByUserId(userId)).thenReturn(List.of());
        SysUserVO enriched = new SysUserVO();
        enriched.setId(userId);
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenReturn(enriched);

        SysUserVO result = applicationA.getById(userId, currentUserId, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        verify(orgStructureService).enrichUser(any(SysUserVO.class));
    }

    @Test
    void getById_userNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(userStore.findUser(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationA.getById(userId, currentUserId, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(com.colonel.saas.common.result.ResultCode.NOT_FOUND.getCode());

        verify(orgStructureService, never()).enrichUser(any());
    }

    // ===== create =====

    @Test
    void create_normalCase_persistsUserAndPublishesEvent() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        List<UUID> roleIds = List.of(roleId);

        SysUserCreateRequest request = new SysUserCreateRequest(
                "newuser", "Passw0rd!", "新用户", "13800000000", "u@x.com",
                null, null, deptId, roleIds);

        ManagedRole role = new ManagedRole(roleId, RoleCodes.BIZ_STAFF, 1);

        when(userStore.findByUsernameIncludingDeleted("newuser")).thenReturn(Optional.empty());
        when(userStore.findRolesByIds(roleIds)).thenReturn(List.of(role));
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-pwd");
        when(userChannelCodePolicy.generateUnique("newuser")).thenReturn("newuser");
        when(orgStructureService.splitAssignment(deptId)).thenReturn(
                new OrgStructureService.SplitAssignment(deptId, deptId, "D", "G", "biz"));
        when(userStore.findRoleIdsByUserId(any(UUID.class))).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserVO result = applicationA.create(request, operatorId);

        assertThat(result).isNotNull();
        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userStore).insertUser(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("newuser");
        assertThat(captor.getValue().encodedPassword()).isEqualTo("encoded-pwd");
        assertThat(captor.getValue().deptId()).isEqualTo(deptId);
        assertThat(captor.getValue().status()).isEqualTo(SysUserStatus.PENDING_ACTIVATION);
        verify(userStore).replaceUserRoles(captor.getValue().id(), roleIds);
        verify(operationLogService).recordSystemAction(
                eq(operatorId), eq("用户管理"), eq("新建用户"), eq("POST"),
                eq("SysUser"), any(String.class), eq("newuser"), any(String.class));
        verify(userDomainEventPublisher).publishUserCreated(
                any(UUID.class), eq("newuser"), eq("新用户"),
                eq(roleId), eq(RoleCodes.BIZ_STAFF),
                eq(deptId), eq(deptId), eq(Integer.valueOf(SysUserStatus.PENDING_ACTIVATION)), eq(operatorId));
    }

    @Test
    void create_duplicateUsername_throwsDuplicate() {
        UUID operatorId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        ManagedUser existing = managedUser(existingId, "alice", UUID.randomUUID(), 1);

        when(userStore.findByUsernameIncludingDeleted("alice")).thenReturn(Optional.of(existing));

        SysUserCreateRequest request = new SysUserCreateRequest(
                "alice", "Passw0rd!", "Alice", "13800000000", "a@x.com",
                null, null, UUID.randomUUID(), List.of());

        assertThatThrownBy(() -> applicationA.create(request, operatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(com.colonel.saas.common.result.ResultCode.DUPLICATE.getCode());

        verify(userStore, never()).insertUser(any());
        verify(userDomainEventPublisher, never()).publishUserCreated(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_softDeletedUsername_restoresUserInsteadOfRejectingIt() {
        UUID operatorId = UUID.randomUUID();
        UUID deletedUserId = UUID.randomUUID();
        ManagedUser deletedUser = new ManagedUser(
                deletedUserId,
                "玄同",
                "旧姓名",
                null,
                null,
                UUID.randomUUID(),
                1,
                false,
                null,
                null,
                1);
        SysUserCreateRequest request = new SysUserCreateRequest(
                "玄同", "Passw0rd!", "新用户", null, null,
                null, null, null, List.of());

        when(userStore.findByUsernameIncludingDeleted("玄同")).thenReturn(Optional.of(deletedUser));
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-pwd");
        when(userChannelCodePolicy.generateUnique("玄同")).thenReturn("user");
        when(userStore.restoreUser(eq(deletedUserId), any(NewUser.class))).thenReturn(true);
        when(userStore.findRoleIdsByUserId(deletedUserId)).thenReturn(List.of());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserVO result = applicationA.create(request, operatorId);

        verify(userStore, never()).insertUser(any());
        verify(userStore).restoreUser(eq(deletedUserId), any(NewUser.class));
        assertThat(result.getId()).isEqualTo(deletedUserId);
        assertThat(result.getUsername()).isEqualTo("玄同");
    }

    private static ManagedUser managedUser(UUID id, String username, UUID deptId, Integer status) {
        return new ManagedUser(
                id,
                username,
                username,
                null,
                null,
                deptId,
                status,
                false,
                null,
                null,
                0);
    }
}
