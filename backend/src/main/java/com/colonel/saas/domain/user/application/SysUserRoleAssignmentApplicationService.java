package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy.AccessibleUser;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore.RoleAssignableRole;
import com.colonel.saas.domain.user.port.UserRoleAssignmentStore.RoleAssignableUser;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserPermissionCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 系统用户角色分配应用服务。
 *
 * <p>承接旧 {@code SysUserService.assignRoles} 写路径，保持全量替换、单一管理员保护、
 * 权限缓存刷新和操作审计语义不变。</p>
 */
@Service
public class SysUserRoleAssignmentApplicationService {

    private final UserRoleAssignmentStore roleAssignmentStore;
    private final OperationLogService operationLogService;
    private final UserPermissionCacheService userPermissionCacheService;
    private final UserAccessPolicy userAccessPolicy;
    private final AuthorizationVersionApplicationService authorizationVersionService;

    public SysUserRoleAssignmentApplicationService(
            UserRoleAssignmentStore roleAssignmentStore,
            OperationLogService operationLogService,
            UserPermissionCacheService userPermissionCacheService,
            UserAccessPolicy userAccessPolicy,
            AuthorizationVersionApplicationService authorizationVersionService) {
        this.roleAssignmentStore = roleAssignmentStore;
        this.operationLogService = operationLogService;
        this.userPermissionCacheService = userPermissionCacheService;
        this.userAccessPolicy = userAccessPolicy;
        this.authorizationVersionService = authorizationVersionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(
            UUID id,
            SysUserAssignRolesRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        RoleAssignableUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, id);
        roleAssignmentStore.replaceUserRoles(id, roleIds);
        authorizationVersionService.incrementUser(
                id,
                "USER_ROLES_REPLACED",
                currentUserId);
        userPermissionCacheService.invalidateUser(id);
        for (UUID roleId : roleIds) {
            userPermissionCacheService.invalidateRole(roleId);
        }
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "分配角色",
                "PUT",
                "SysUser",
                user.id().toString(),
                user.username(),
                "更新用户角色: " + user.username()
        );
    }

    private RoleAssignableUser requireUser(UUID id) {
        return roleAssignmentStore.findUser(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private static AccessibleUser accessibleUser(RoleAssignableUser user) {
        return new AccessibleUser(user.id(), user.deptId());
    }

    private List<UUID> normalizeRoleIds(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            if (roleId != null) {
                distinct.add(roleId);
            }
        }
        return new ArrayList<>(distinct);
    }

    private void validateRoleIds(List<UUID> roleIds, UUID targetUserId) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RoleAssignableRole> roles = roleAssignmentStore.findRolesByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.status() == null || role.status() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        assertSingleAdminUser(roles, targetUserId);
    }

    private void assertSingleAdminUser(List<RoleAssignableRole> roles, UUID targetUserId) {
        RoleAssignableRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.roleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.id() == null) {
            return;
        }
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = roleAssignmentStore.findRoleIdsByUserId(targetUserId).stream()
                    .anyMatch(roleId -> adminRole.id().equals(roleId));
            if (targetAlreadyAdmin) {
                return;
            }
        }
        List<UUID> adminUserIds = roleAssignmentStore.findUserIdsByRoleId(adminRole.id());
        if (adminUserIds.isEmpty()) {
            return;
        }
        boolean hasExistingAdmin = roleAssignmentStore.findUsersByIds(adminUserIds).stream()
                .filter(Objects::nonNull)
                .anyMatch(user -> user.deleted() == null || user.deleted() == 0);
        if (hasExistingAdmin) {
            throw BusinessException.duplicate("管理员账号已存在，不能新增或转配第二个管理员");
        }
    }
}
