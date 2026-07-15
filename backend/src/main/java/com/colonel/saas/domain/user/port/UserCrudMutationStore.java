package com.colonel.saas.domain.user.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 系统用户资料维护写路径所需的用户与角色关系存储端口。
 */
public interface UserCrudMutationStore {

    Optional<ManagedUser> findUser(UUID userId);

    Optional<ManagedUser> findByUsername(String username);

    /**
     * 查询包含软删除记录的用户名，用于创建时遵守数据库全局唯一约束。
     */
    Optional<ManagedUser> findByUsernameIncludingDeleted(String username);

    List<ManagedRole> findRolesByIds(Collection<UUID> roleIds);

    List<UUID> findUserIdsByRoleId(UUID roleId);

    List<ManagedUser> findUsersByIds(Collection<UUID> userIds);

    void insertUser(NewUser user);

    void saveUser(ManagedUser user);

    void replaceUserRoles(UUID userId, List<UUID> roleIds);

    void deleteUserRoles(UUID userId);

    void softDeleteUser(UUID userId);

    void updatePassword(UUID userId, String encodedPassword, boolean forcePasswordChange);

    List<UUID> findRoleIdsByUserId(UUID userId);

    record ManagedUser(
            UUID id,
            String username,
            String realName,
            String phone,
            String email,
            UUID deptId,
            Integer status,
            Boolean forcePasswordChange,
            LocalDateTime lastLoginAt,
            LocalDateTime createTime,
            Integer deleted) {

        public ManagedUser withProfile(
                String newRealName,
                String newPhone,
                String newEmail,
                UUID newDeptId,
                Integer newStatus) {
            return new ManagedUser(
                    id,
                    username,
                    newRealName,
                    newPhone,
                    newEmail,
                    newDeptId,
                    newStatus,
                    forcePasswordChange,
                    lastLoginAt,
                    createTime,
                    deleted);
        }
    }

    record NewUser(
            UUID id,
            String username,
            String encodedPassword,
            String realName,
            String phone,
            String email,
            UUID deptId,
            Integer status,
            Boolean forcePasswordChange,
            String channelCode) {

        public ManagedUser toManagedUser() {
            return new ManagedUser(
                    id,
                    username,
                    realName,
                    phone,
                    email,
                    deptId,
                    status,
                    forcePasswordChange,
                    null,
                    null,
                    0);
        }
    }

    record ManagedRole(UUID id, String roleCode, Integer status) {
    }
}
