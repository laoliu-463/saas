package com.colonel.saas.domain.user.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户负责人分配校验所需的读模型端口。
 */
public interface UserAssignmentLookup {

    Optional<AssignableUser> findUser(UUID userId);

    List<UserRoleAssignment> findUserRoles(UUID userId);

    Map<UUID, AssignableRole> findRolesByIds(Collection<UUID> roleIds);

    record AssignableUser(UUID id, UUID deptId, Integer status, Integer deleted) {
        public AssignableUser(UUID id, UUID deptId, Integer status) {
            this(id, deptId, status, 0);
        }
    }

    record UserRoleAssignment(UUID userId, UUID roleId) {
    }

    record AssignableRole(UUID id, String roleCode, Integer status) {
    }
}
