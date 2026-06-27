package com.colonel.saas.domain.user.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户业务组成员变更所需的最小持久化端口。
 */
public interface UserGroupMembershipStore {

    Optional<GroupMember> findMember(UUID userId);

    void updateDept(UUID userId, UUID deptId);

    record GroupMember(UUID id, String username, UUID deptId) {

        public GroupMember withDept(UUID newDeptId) {
            return new GroupMember(id, username, newDeptId);
        }
    }
}
