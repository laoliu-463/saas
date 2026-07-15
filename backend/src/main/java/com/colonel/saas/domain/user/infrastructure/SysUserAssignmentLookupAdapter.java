package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserAssignmentLookup;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通过现有 sys_user / sys_role / sys_user_role 读取负责人分配校验读模型。
 */
@Component
public class SysUserAssignmentLookupAdapter implements UserAssignmentLookup {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserAssignmentLookupAdapter(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public Optional<AssignableUser> findUser(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return sysUserMapper.findActiveById(userId).map(SysUserAssignmentLookupAdapter::toAssignableUser);
    }

    @Override
    public List<UserRoleAssignment> findUserRoles(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByUserId(userId);
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        return relations.stream()
                .filter(Objects::nonNull)
                .map(relation -> new UserRoleAssignment(relation.getUserId(), relation.getRoleId()))
                .toList();
    }

    @Override
    public Map<UUID, AssignableRole> findRolesByIds(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(distinct);
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> new AssignableRole(role.getId(), role.getRoleCode(), role.getStatus()))
                .filter(role -> role.id() != null)
                .collect(Collectors.toMap(AssignableRole::id, Function.identity(), (left, right) -> left));
    }

    private static AssignableUser toAssignableUser(SysUser user) {
        if (user == null) {
            return null;
        }
        return new AssignableUser(user.getId(), user.getDeptId(), user.getStatus(), user.getDeleted());
    }
}
