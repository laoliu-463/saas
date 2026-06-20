package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy.AccessibleUser;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
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

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OperationLogService operationLogService;
    private final UserPermissionCacheService userPermissionCacheService;
    private final UserAccessPolicy userAccessPolicy;

    public SysUserRoleAssignmentApplicationService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OperationLogService operationLogService,
            UserPermissionCacheService userPermissionCacheService,
            UserAccessPolicy userAccessPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.operationLogService = operationLogService;
        this.userPermissionCacheService = userPermissionCacheService;
        this.userAccessPolicy = userAccessPolicy;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(
            UUID id,
            SysUserAssignRolesRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, id);
        replaceUserRoles(id, roleIds);
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
                user.getId().toString(),
                user.getUsername(),
                "更新用户角色: " + user.getUsername()
        );
    }

    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private static AccessibleUser accessibleUser(SysUser user) {
        return new AccessibleUser(user.getId(), user.getDeptId());
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
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        assertSingleAdminUser(roles, targetUserId);
    }

    private void assertSingleAdminUser(List<SysRole> roles, UUID targetUserId) {
        SysRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.getRoleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.getId() == null) {
            return;
        }
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = sysUserRoleMapper.findByUserId(targetUserId).stream()
                    .anyMatch(relation -> adminRole.getId().equals(relation.getRoleId()));
            if (targetAlreadyAdmin) {
                return;
            }
        }
        List<UUID> adminUserIds = sysUserRoleMapper.findByRoleId(adminRole.getId()).stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (adminUserIds.isEmpty()) {
            return;
        }
        boolean hasExistingAdmin = sysUserMapper.selectBatchIds(adminUserIds).stream()
                .filter(Objects::nonNull)
                .anyMatch(user -> user.getDeleted() == null || user.getDeleted() == 0);
        if (hasExistingAdmin) {
            throw BusinessException.duplicate("管理员账号已存在，不能新增或转配第二个管理员");
        }
    }

    private void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }
}
