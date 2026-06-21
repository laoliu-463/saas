package com.colonel.saas.domain.user.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 系统用户角色分配写路径所需的用户、角色与关系存储端口。
 */
public interface UserRoleAssignmentStore {

    Optional<RoleAssignableUser> findUser(UUID userId);

    List<RoleAssignableRole> findRolesByIds(Collection<UUID> roleIds);

    List<UUID> findRoleIdsByUserId(UUID userId);

    List<UUID> findUserIdsByRoleId(UUID roleId);

    List<RoleAssignableUser> findUsersByIds(Collection<UUID> userIds);

    void replaceUserRoles(UUID userId, List<UUID> roleIds);

    record RoleAssignableUser(UUID id, String username, UUID deptId, Integer deleted) {
    }

    record RoleAssignableRole(UUID id, String roleCode, Integer status) {
    }
}
