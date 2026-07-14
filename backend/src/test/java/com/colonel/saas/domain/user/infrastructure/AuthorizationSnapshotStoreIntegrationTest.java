package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationSnapshotStoreIntegrationTest extends BaseIntegrationTest {

    private static final int ACTIVE = 1;
    private static final int INACTIVE = 0;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    @Autowired
    private SysAuthorizationSnapshotStoreAdapter store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadActiveSnapshot_shouldOnlyReturnGrantsFromActiveRolesAndPermissions() {
        UUID userId = insertUser(UUID.randomUUID(), 7L, ACTIVE, NOT_DELETED);
        UUID activeRoleId = insertRole(UUID.randomUUID(), "active_reader", ACTIVE, NOT_DELETED);
        UUID disabledRoleId = insertRole(UUID.randomUUID(), "disabled_reader", INACTIVE, NOT_DELETED);
        UUID softDeletedRoleId = insertRole(UUID.randomUUID(), "soft_deleted_role", ACTIVE, DELETED);
        UUID deletedMembershipRoleId = insertRole(
                UUID.randomUUID(), "deleted_membership_role", ACTIVE, NOT_DELETED);
        UUID scopeOnlyRoleId = insertRole(UUID.randomUUID(), "scope_only", ACTIVE, NOT_DELETED);
        UUID activePermissionId = insertPermission("sample", "read", true, ACTIVE, NOT_DELETED);
        UUID disabledPermissionId = insertPermission("sample", "delete", true, INACTIVE, NOT_DELETED);
        UUID softDeletedPermissionId = insertPermission("sample", "archive", true, ACTIVE, DELETED);

        assignRole(userId, activeRoleId);
        assignRole(userId, disabledRoleId);
        assignRole(userId, softDeletedRoleId);
        assignRole(userId, deletedMembershipRoleId, DELETED);
        assignRole(userId, scopeOnlyRoleId);
        grantPermission(activeRoleId, activePermissionId);
        grantPermission(activeRoleId, disabledPermissionId);
        grantPermission(activeRoleId, softDeletedPermissionId);
        grantPermission(disabledRoleId, activePermissionId);
        grantPermission(softDeletedRoleId, activePermissionId);
        grantPermission(deletedMembershipRoleId, activePermissionId);
        insertDomainScope(activeRoleId, "sample", "GROUP");
        insertDomainScope(disabledRoleId, "sample", "ALL");
        insertDomainScope(softDeletedRoleId, "sample", "ALL");
        insertDomainScope(deletedMembershipRoleId, "sample", "ALL");
        insertDomainScope(scopeOnlyRoleId, "sample", "ALL");

        AuthorizationSnapshot snapshot = store.loadActiveSnapshot(userId, 7L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(userId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(7L);
        assertThat(snapshot.grants())
                .extracting(GrantedRolePermission::roleId)
                .doesNotContain(softDeletedRoleId, deletedMembershipRoleId);
        assertThat(snapshot.grants())
                .extracting(grant -> grant.permission().value())
                .doesNotContain("sample:archive");
        assertThat(snapshot.grants())
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.roleId()).isEqualTo(activeRoleId);
                    assertThat(grant.permission().value()).isEqualTo("sample:read");
                    assertThat(grant.domainCode()).isEqualTo("sample");
                    assertThat(grant.dataScopeRequired()).isTrue();
                    assertThat(grant.scope()).isEqualTo(AuthorizationScope.GROUP);
                });
    }

    @Test
    void loadActiveSnapshot_whenUserIsMissing_shouldReturnEmpty() {
        assertThat(store.loadActiveSnapshot(UUID.randomUUID(), 1L)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenUserIsInactiveOrDeleted_shouldReturnEmpty() {
        UUID inactiveUserId = insertUser(UUID.randomUUID(), 2L, INACTIVE, NOT_DELETED);
        UUID deletedUserId = insertUser(UUID.randomUUID(), 3L, ACTIVE, DELETED);

        assertThat(store.loadActiveSnapshot(inactiveUserId, 2L)).isEmpty();
        assertThat(store.loadActiveSnapshot(deletedUserId, 3L)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenActiveUserHasNoGrant_shouldReturnSubjectWithEmptyGrants() {
        UUID deptId = UUID.randomUUID();
        UUID userId = insertUser(deptId, 5L, ACTIVE, NOT_DELETED);

        AuthorizationSnapshot snapshot = store.loadActiveSnapshot(userId, 5L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(userId);
        assertThat(snapshot.subject().deptId()).isEqualTo(deptId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(5L);
        assertThat(snapshot.grants()).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenGrantedPermissionHasNoDomainScope_shouldMapGrantToDeny() {
        UUID userId = insertUser(UUID.randomUUID(), 4L, ACTIVE, NOT_DELETED);
        UUID roleId = insertRole(UUID.randomUUID(), "missing_scope", ACTIVE, NOT_DELETED);
        UUID permissionId = insertPermission("sample", "read", true, ACTIVE, NOT_DELETED);
        assignRole(userId, roleId);
        grantPermission(roleId, permissionId);

        AuthorizationSnapshot snapshot = store.loadActiveSnapshot(userId, 4L).orElseThrow();

        assertThat(snapshot.grants())
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.roleId()).isEqualTo(roleId);
                    assertThat(grant.permission().value()).isEqualTo("sample:read");
                    assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY);
                });
    }

    @Test
    void loadActiveSnapshot_whenMultipleRolesGrantSamePermission_shouldPreserveSortedRowsAndSubject() {
        UUID deptId = UUID.randomUUID();
        UUID userId = insertUser(deptId, 9L, ACTIVE, NOT_DELETED);
        UUID firstRoleId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondRoleId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID permissionId = insertPermission("sample", "read", true, ACTIVE, NOT_DELETED);
        insertRole(secondRoleId, "second_reader", ACTIVE, NOT_DELETED);
        insertRole(firstRoleId, "first_reader", ACTIVE, NOT_DELETED);
        assignRole(userId, secondRoleId);
        assignRole(userId, firstRoleId);
        grantPermission(secondRoleId, permissionId);
        grantPermission(firstRoleId, permissionId);
        insertDomainScope(secondRoleId, "sample", "ALL");
        insertDomainScope(firstRoleId, "sample", "SELF");

        AuthorizationSnapshot snapshot = store.loadActiveSnapshot(userId, 9L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(userId);
        assertThat(snapshot.subject().deptId()).isEqualTo(deptId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(9L);
        assertThat(snapshot.grants())
                .extracting(GrantedRolePermission::roleId)
                .containsExactly(firstRoleId, secondRoleId);
        assertThat(snapshot.grants())
                .extracting(grant -> grant.permission().value())
                .containsExactly("sample:read", "sample:read");
        assertThat(snapshot.grants())
                .extracting(GrantedRolePermission::scope)
                .containsExactly(AuthorizationScope.SELF, AuthorizationScope.ALL);
    }

    @Test
    void loadActiveSnapshot_whenExpectedVersionIsStaleOrFuture_shouldReturnEmpty() {
        UUID userId = insertUser(UUID.randomUUID(), 12L, ACTIVE, NOT_DELETED);

        assertThat(store.loadActiveSnapshot(userId, 11L)).isEmpty();
        assertThat(store.loadActiveSnapshot(userId, 13L)).isEmpty();
        assertThat(store.loadActiveSnapshot(userId, 12L)).isPresent();
    }

    private UUID insertUser(UUID deptId, long authzVersion, int status, int deleted) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update("""
                        INSERT INTO sys_user (
                            id, username, password, real_name, dept_id, channel_code,
                            status, deleted, authz_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "user_" + suffix,
                "test-password-hash",
                "Authorization Test User",
                deptId,
                "c" + suffix,
                status,
                deleted,
                authzVersion);
        return userId;
    }

    private UUID insertRole(UUID roleId, String roleCode, int status, int deleted) {
        jdbcTemplate.update("""
                        INSERT INTO sys_role (
                            id, role_code, role_name, data_scope, status, deleted
                        ) VALUES (?, ?, ?, 1, ?, ?)
                        """,
                roleId,
                roleCode,
                roleCode,
                status,
                deleted);
        return roleId;
    }

    private UUID insertPermission(
            String resourceCode,
            String actionCode,
            boolean dataScopeRequired,
            int status,
            int deleted) {
        UUID permissionId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO sys_permission (
                            id, permission_code, domain_code, resource_code, action_code,
                            data_scope_required, status, deleted
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                permissionId,
                resourceCode + ":" + actionCode,
                resourceCode,
                resourceCode,
                actionCode,
                dataScopeRequired,
                status,
                deleted);
        return permissionId;
    }

    private void assignRole(UUID userId, UUID roleId) {
        assignRole(userId, roleId, NOT_DELETED);
    }

    private void assignRole(UUID userId, UUID roleId, int deleted) {
        jdbcTemplate.update("""
                        INSERT INTO sys_user_role (id, user_id, role_id, deleted)
                        VALUES (?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                userId,
                roleId,
                deleted);
    }

    private void grantPermission(UUID roleId, UUID permissionId) {
        jdbcTemplate.update("""
                        INSERT INTO sys_role_permission (role_id, permission_id)
                        VALUES (?, ?)
                        """,
                roleId,
                permissionId);
    }

    private void insertDomainScope(UUID roleId, String domainCode, String scopeCode) {
        jdbcTemplate.update("""
                        INSERT INTO sys_role_domain_scope (role_id, domain_code, scope_code)
                        VALUES (?, ?, ?)
                        """,
                roleId,
                domainCode,
                scopeCode);
    }
}
