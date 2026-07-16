package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserRoleCodeLookup;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 基于用户角色关联表实现的批量角色编码查询。 */
@Component
public class SysUserRoleCodeLookupAdapter implements UserRoleCodeLookup {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public SysUserRoleCodeLookupAdapter(
            SysUserRoleMapper userRoleMapper,
            SysRoleMapper roleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public Map<UUID, Set<String>> findActiveRoleCodesByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }

        List<SysUserRole> relations = userRoleMapper.findByUserIds(distinctUserIds);
        List<UUID> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, SysRole> activeRoles = roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .filter(role -> !Integer.valueOf(1).equals(role.getDeleted()))
                .filter(role -> role.getRoleCode() != null && !role.getRoleCode().isBlank())
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (left, right) -> left));

        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        for (SysUserRole relation : relations) {
            if (relation == null || relation.getUserId() == null) {
                continue;
            }
            SysRole role = activeRoles.get(relation.getRoleId());
            if (role == null) {
                continue;
            }
            result.computeIfAbsent(relation.getUserId(), ignored -> new LinkedHashSet<>())
                    .add(role.getRoleCode().trim().toLowerCase());
        }

        Map<UUID, Set<String>> immutable = new LinkedHashMap<>();
        result.forEach((userId, roles) -> immutable.put(userId, Set.copyOf(roles)));
        return Map.copyOf(immutable);
    }
}
