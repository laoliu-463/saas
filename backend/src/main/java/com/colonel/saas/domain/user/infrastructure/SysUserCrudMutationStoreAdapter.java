package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserCrudMutationStore;
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
 * 通过现有 sys_user / sys_user_role 持久化系统用户资料维护写路径。
 */
@Component
public class SysUserCrudMutationStoreAdapter implements UserCrudMutationStore {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserCrudMutationStoreAdapter(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public Optional<ManagedUser> findUser(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toManagedUser(sysUserMapper.selectById(userId)));
    }

    @Override
    public Optional<ManagedUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return sysUserMapper.findByUsername(username.trim()).map(this::toManagedUser);
    }

    @Override
    public Optional<ManagedUser> findByUsernameIncludingDeleted(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return sysUserMapper.findByUsernameIncludingDeleted(username.trim()).map(this::toManagedUser);
    }

    @Override
    public List<ManagedRole> findRolesByIds(Collection<UUID> roleIds) {
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
                .map(role -> new ManagedRole(role.getId(), role.getRoleCode(), role.getStatus()))
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
    public List<ManagedUser> findUsersByIds(Collection<UUID> userIds) {
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
                .map(this::toManagedUser)
                .toList();
    }

    @Override
    public void insertUser(NewUser user) {
        SysUser entity = new SysUser();
        entity.setId(user.id());
        entity.setUsername(user.username());
        entity.setPassword(user.encodedPassword());
        entity.setRealName(user.realName());
        entity.setPhone(user.phone());
        entity.setEmail(user.email());
        entity.setDeptId(user.deptId());
        entity.setStatus(user.status());
        entity.setForcePasswordChange(user.forcePasswordChange());
        entity.setChannelCode(user.channelCode());
        sysUserMapper.insert(entity);
    }

    @Override
    public void saveUser(ManagedUser user) {
        SysUser entity = new SysUser();
        entity.setId(user.id());
        entity.setUsername(user.username());
        entity.setRealName(user.realName());
        entity.setPhone(user.phone());
        entity.setEmail(user.email());
        entity.setDeptId(user.deptId());
        entity.setStatus(user.status());
        entity.setForcePasswordChange(user.forcePasswordChange());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setCreateTime(user.createTime());
        sysUserMapper.updateById(entity);
    }

    @Override
    public void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (UUID roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    @Override
    public void deleteUserRoles(UUID userId) {
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
    }

    @Override
    public void softDeleteUser(UUID userId) {
        sysUserMapper.softDeleteById(userId);
    }

    @Override
    public void updatePassword(UUID userId, String encodedPassword, boolean forcePasswordChange) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(encodedPassword);
        update.setForcePasswordChange(forcePasswordChange);
        sysUserMapper.updateById(update);
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

    private ManagedUser toManagedUser(SysUser user) {
        if (user == null) {
            return null;
        }
        return new ManagedUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getEmail(),
                user.getDeptId(),
                user.getStatus(),
                user.getForcePasswordChange(),
                user.getLastLoginAt(),
                user.getCreateTime(),
                user.getDeleted());
    }
}
