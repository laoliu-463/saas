package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.service.SysMenuService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotCache;
import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationVersionWriterIntegrationTest extends BaseIntegrationTest {

    private static final RuntimeException WRITER_FAILURE =
            new RuntimeException("forced failure after authorization version update");

    @Autowired
    private SysUserRoleAssignmentApplicationService roleAssignmentWriter;
    @Autowired
    private SysMenuService menuWriter;
    @Autowired
    private SysUserCRUDApplicationB userCrudWriter;
    @Autowired
    private SysUserGroupMembershipApplication groupMembershipWriter;
    @Autowired
    private AuthorizationVersionStore authorizationVersionStore;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AuthorizationVersionApplicationService authorizationVersionService;
    @MockBean
    private AuthorizationSnapshotCache authorizationSnapshotCache;
    @MockBean
    private OperationLogService operationLogService;
    @MockBean
    private UserDomainEventPublisher userDomainEventPublisher;
    @MockBean
    private UserPermissionCacheService userPermissionCacheService;
    @MockBean
    private UserAccessPolicy userAccessPolicy;
    @MockBean
    private OrgStructureService orgStructureService;

    private AuthorizationVersionApplicationService realVersionDelegate;
    private boolean factMutationObservedBeforeFailure;
    private Long versionObservedBeforeFailure;

    @BeforeEach
    void prepareWriterSchemaAndFailureProbe() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_role_menu (
                    role_id UUID NOT NULL,
                    menu_id UUID NOT NULL,
                    PRIMARY KEY (role_id, menu_id)
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE sys_role_menu");
        realVersionDelegate = new AuthorizationVersionApplicationService(
                authorizationVersionStore,
                applicationEventPublisher);
        factMutationObservedBeforeFailure = false;
        versionObservedBeforeFailure = null;
    }

    @Test
    void assignRoles_rollsBackRoleFactsAndAuthorizationVersion() {
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID originalRoleId = insertRole("original-role");
        UUID replacementRoleId = insertRole("replacement-role");
        insertUser(userId, 7L, SysUserStatus.ACTIVE, null, "Role Writer");
        insertUserRole(userId, originalRoleId);
        failAfterIncrementUser(
                userId,
                "USER_ROLES_REPLACED",
                actorUserId,
                () -> assertThat(selectRoleIds(userId)).containsExactly(replacementRoleId));

        assertThatThrownBy(() -> roleAssignmentWriter.assignRoles(
                userId,
                new SysUserAssignRolesRequest(List.of(replacementRoleId)),
                actorUserId,
                DataScope.ALL))
                .isSameAs(WRITER_FAILURE);

        assertThat(factMutationObservedBeforeFailure).isTrue();
        assertThat(versionObservedBeforeFailure).isEqualTo(8L);
        assertThat(selectRoleIds(userId)).containsExactly(originalRoleId);
        assertThat(selectVersion(userId)).isEqualTo(7L);
        verify(authorizationVersionService).incrementUser(
                userId,
                "USER_ROLES_REPLACED",
                actorUserId);
        verify(authorizationSnapshotCache, never()).evict(any(), anyLong());
    }

    @Test
    void assignMenusToRole_rollsBackMenuFactsAndAffectedUserAuthorizationVersion() {
        UUID roleId = insertRole("menu-role");
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID originalMenuId = UUID.randomUUID();
        UUID replacementMenuId = UUID.randomUUID();
        insertUser(userId, 11L, SysUserStatus.ACTIVE, null, "Menu Writer");
        insertUserRole(userId, roleId);
        insertRoleMenu(roleId, originalMenuId);
        failAfterIncrementUsersByRole(
                roleId,
                userId,
                "ROLE_MENU_PERMISSIONS_UPDATED",
                actorUserId,
                () -> assertThat(selectMenuIds(roleId)).containsExactly(replacementMenuId));

        assertThatThrownBy(() -> menuWriter.assignMenusToRole(
                roleId,
                List.of(replacementMenuId),
                actorUserId))
                .isSameAs(WRITER_FAILURE);

        assertThat(factMutationObservedBeforeFailure).isTrue();
        assertThat(versionObservedBeforeFailure).isEqualTo(12L);
        assertThat(selectMenuIds(roleId)).containsExactly(originalMenuId);
        assertThat(selectVersion(userId)).isEqualTo(11L);
        verify(authorizationVersionService).incrementUsersByRole(
                roleId,
                "ROLE_MENU_PERMISSIONS_UPDATED",
                actorUserId);
        verify(authorizationSnapshotCache, never()).evict(any(), anyLong());
    }

    @Test
    void updateUserAuthorizationContext_rollsBackUserFactAndAuthorizationVersion() {
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        insertUser(userId, 17L, SysUserStatus.ACTIVE, null, "Original Name");
        failAfterIncrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                actorUserId,
                () -> {
                    assertThat(selectUserStatus(userId)).isEqualTo(SysUserStatus.DISABLED);
                    assertThat(selectRealName(userId)).isEqualTo("Changed Name");
                });

        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "Changed Name",
                null,
                null,
                SysUserStatus.DISABLED,
                null,
                null,
                null);

        assertThatThrownBy(() -> userCrudWriter.update(
                userId,
                request,
                actorUserId,
                DataScope.ALL))
                .isSameAs(WRITER_FAILURE);

        assertThat(factMutationObservedBeforeFailure).isTrue();
        assertThat(versionObservedBeforeFailure).isEqualTo(18L);
        assertThat(selectUserStatus(userId)).isEqualTo(SysUserStatus.ACTIVE);
        assertThat(selectRealName(userId)).isEqualTo("Original Name");
        assertThat(selectVersion(userId)).isEqualTo(17L);
        verify(authorizationVersionService).incrementUser(
                userId,
                "USER_AUTHORIZATION_CONTEXT_UPDATED",
                actorUserId);
        verify(authorizationSnapshotCache, never()).evict(any(), anyLong());
    }

    @Test
    void assignUsersToGroup_rollsBackDepartmentFactAndAuthorizationVersion() {
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID originalDeptId = UUID.randomUUID();
        UUID replacementDeptId = UUID.randomUUID();
        insertUser(userId, 23L, SysUserStatus.ACTIVE, originalDeptId, "Group Writer");
        when(orgStructureService.resolveAssignment(null, groupId))
                .thenReturn(new OrgStructureService.ResolvedAssignment(
                        replacementDeptId,
                        null,
                        groupId));
        when(orgStructureService.splitAssignment(originalDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(
                        originalDeptId,
                        null,
                        "original",
                        null,
                        "department"));
        when(orgStructureService.splitAssignment(replacementDeptId))
                .thenReturn(new OrgStructureService.SplitAssignment(
                        null,
                        groupId,
                        null,
                        "replacement",
                        "biz"));
        when(orgStructureService.formatOrgChangeRemark(
                userId,
                originalDeptId,
                replacementDeptId,
                actorUserId))
                .thenReturn("group changed");
        failAfterIncrementUser(
                userId,
                "USER_GROUP_MEMBERSHIP_UPDATED",
                actorUserId,
                () -> assertThat(selectDeptId(userId)).isEqualTo(replacementDeptId));

        assertThatThrownBy(() -> groupMembershipWriter.assignUsersToGroup(
                groupId,
                List.of(userId),
                actorUserId))
                .isSameAs(WRITER_FAILURE);

        assertThat(factMutationObservedBeforeFailure).isTrue();
        assertThat(versionObservedBeforeFailure).isEqualTo(24L);
        assertThat(selectDeptId(userId)).isEqualTo(originalDeptId);
        assertThat(selectVersion(userId)).isEqualTo(23L);
        verify(authorizationVersionService).incrementUser(
                userId,
                "USER_GROUP_MEMBERSHIP_UPDATED",
                actorUserId);
        verify(authorizationSnapshotCache, never()).evict(any(), anyLong());
    }

    private void failAfterIncrementUser(
            UUID userId,
            String cause,
            UUID actorUserId,
            Runnable inTransactionFactAssertion) {
        doAnswer(invocation -> {
            inTransactionFactAssertion.run();
            factMutationObservedBeforeFailure = true;
            realVersionDelegate.incrementUser(userId, cause, actorUserId);
            versionObservedBeforeFailure = selectVersion(userId);
            throw WRITER_FAILURE;
        }).when(authorizationVersionService).incrementUser(
                userId,
                cause,
                actorUserId);
    }

    private void failAfterIncrementUsersByRole(
            UUID roleId,
            UUID affectedUserId,
            String cause,
            UUID actorUserId,
            Runnable inTransactionFactAssertion) {
        doAnswer(invocation -> {
            inTransactionFactAssertion.run();
            factMutationObservedBeforeFailure = true;
            realVersionDelegate.incrementUsersByRole(roleId, cause, actorUserId);
            versionObservedBeforeFailure = selectVersion(affectedUserId);
            throw WRITER_FAILURE;
        }).when(authorizationVersionService).incrementUsersByRole(
                roleId,
                cause,
                actorUserId);
    }

    private void insertUser(
            UUID userId,
            long authzVersion,
            int status,
            UUID deptId,
            String realName) {
        String suffix = suffix(userId);
        jdbcTemplate.update("""
                        INSERT INTO sys_user (
                            id, username, password, real_name, channel_code,
                            dept_id, status, deleted, authz_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                        """,
                userId,
                "writer_user_" + suffix,
                "test-password-hash",
                realName,
                "w" + suffix,
                deptId,
                status,
                authzVersion);
    }

    private UUID insertRole(String label) {
        UUID roleId = UUID.randomUUID();
        String suffix = suffix(roleId);
        jdbcTemplate.update("""
                        INSERT INTO sys_role (
                            id, role_code, role_name, data_scope, status, deleted
                        ) VALUES (?, ?, ?, 1, 1, 0)
                        """,
                roleId,
                "writer_" + suffix,
                label);
        return roleId;
    }

    private void insertUserRole(UUID userId, UUID roleId) {
        jdbcTemplate.update("""
                        INSERT INTO sys_user_role (id, user_id, role_id, deleted)
                        VALUES (?, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                userId,
                roleId);
    }

    private void insertRoleMenu(UUID roleId, UUID menuId) {
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)",
                roleId,
                menuId);
    }

    private List<UUID> selectRoleIds(UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id = ? ORDER BY role_id",
                UUID.class,
                userId);
    }

    private List<UUID> selectMenuIds(UUID roleId) {
        return jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id",
                UUID.class,
                roleId);
    }

    private long selectVersion(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT authz_version FROM sys_user WHERE id = ?",
                Long.class,
                userId);
    }

    private int selectUserStatus(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?",
                Integer.class,
                userId);
    }

    private String selectRealName(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT real_name FROM sys_user WHERE id = ?",
                String.class,
                userId);
    }

    private UUID selectDeptId(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE id = ?",
                UUID.class,
                userId);
    }

    private static String suffix(UUID id) {
        return id.toString().replace("-", "").substring(20);
    }
}
