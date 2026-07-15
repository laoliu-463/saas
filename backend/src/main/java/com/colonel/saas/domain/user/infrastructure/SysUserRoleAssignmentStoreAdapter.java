package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserRoleAssignmentStore;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 sys_user / sys_role / sys_user_role 持久化用户角色分配。
 */
@Component
public class SysUserRoleAssignmentStoreAdapter implements UserRoleAssignmentStore {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserRoleAssignmentStoreAdapter(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public Optional<RoleAssignableUser> findUser(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return sysUserMapper.findActiveById(userId).map(this::toUser);
    }

    @Override
    public List<RoleAssignableRole> findRolesByIds(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(distinct);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> new RoleAssignableRole(role.getId(), role.getRoleCode(), role.getStatus()))
                .toList();
    }

    @Override
    public List<UUID> findRoleIdsByUserId(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByUserId(userId);
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        return relations.stream()
                .filter(Objects::nonNull)
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<UUID> findUserIdsByRoleId(UUID roleId) {
        if (roleId == null) {
            return List.of();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByRoleId(roleId);
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        return relations.stream()
                .filter(Objects::nonNull)
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<RoleAssignableUser> findUsersByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(distinct);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .map(this::toUser)
                .toList();
    }

    @Override
    public void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    private RoleAssignableUser toUser(SysUser user) {
        if (user == null) {
            return null;
        }
        return new RoleAssignableUser(user.getId(), user.getUsername(), user.getDeptId(), user.getDeleted());
    }
}
