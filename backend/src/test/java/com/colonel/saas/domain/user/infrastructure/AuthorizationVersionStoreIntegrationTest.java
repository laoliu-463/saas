package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.AuthorizationVersionStore;
import com.colonel.saas.mapper.AuthorizationVersionMapper;
import com.colonel.saas.mapper.projection.AuthorizationVersionChangeRow;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizationVersionStoreIntegrationTest extends BaseIntegrationTest {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    @Autowired
    private SysAuthorizationVersionStoreAdapter store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void incrementUser_returnsExactPreviousAndCurrentVersion() {
        UUID userId = insertUser(UUID.randomUUID(), 4L, NOT_DELETED);

        List<AuthorizationVersionStore.VersionChange> changes = store.incrementUser(userId);

        assertThat(changes).containsExactly(
                new AuthorizationVersionStore.VersionChange(userId, 4L, 5L));
        assertThat(selectVersion(userId)).isEqualTo(5L);
        assertThatThrownBy(() -> changes.add(
                new AuthorizationVersionStore.VersionChange(userId, 5L, 6L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void incrementUser_twiceInSameTransaction_returnsContiguousChangesAndUpdatesTwice() {
        UUID userId = insertUser(UUID.randomUUID(), 4L, NOT_DELETED);

        List<List<AuthorizationVersionStore.VersionChange>> calls =
                new TransactionTemplate(transactionManager).execute(status -> List.of(
                        store.incrementUser(userId),
                        store.incrementUser(userId)));

        assertThat(calls).containsExactly(
                List.of(new AuthorizationVersionStore.VersionChange(userId, 4L, 5L)),
                List.of(new AuthorizationVersionStore.VersionChange(userId, 5L, 6L)));
        assertThat(selectVersion(userId)).isEqualTo(6L);
    }

    @Test
    void incrementUser_updatesOnlyNonDeletedUser() {
        UUID deletedUserId = insertUser(UUID.randomUUID(), 7L, DELETED);

        assertThat(store.incrementUser(deletedUserId)).isEmpty();
        assertThat(store.incrementUser(UUID.randomUUID())).isEmpty();
        assertThat(selectVersion(deletedUserId)).isEqualTo(7L);
    }

    @Test
    void incrementUsersByRole_updatesOnlyActiveRelationsOnceAndReturnsSortedChanges() {
        UUID roleId = insertRole();
        UUID firstUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID secondUserId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID deletedRelationUserId = UUID.fromString("00000000-0000-0000-0000-000000000033");
        UUID unrelatedUserId = UUID.fromString("00000000-0000-0000-0000-000000000044");
        UUID deletedUserId = UUID.fromString("00000000-0000-0000-0000-000000000055");
        insertUser(secondUserId, 5L, NOT_DELETED);
        insertUser(firstUserId, 2L, NOT_DELETED);
        insertUser(deletedRelationUserId, 8L, NOT_DELETED);
        insertUser(unrelatedUserId, 11L, NOT_DELETED);
        insertUser(deletedUserId, 13L, DELETED);
        assignRole(secondUserId, roleId, NOT_DELETED);
        assignRole(firstUserId, roleId, NOT_DELETED);
        assignRole(firstUserId, roleId, NOT_DELETED);
        assignRole(deletedRelationUserId, roleId, DELETED);
        assignRole(deletedUserId, roleId, NOT_DELETED);

        List<AuthorizationVersionStore.VersionChange> changes =
                store.incrementUsersByRole(roleId);

        assertThat(changes).containsExactly(
                new AuthorizationVersionStore.VersionChange(firstUserId, 2L, 3L),
                new AuthorizationVersionStore.VersionChange(secondUserId, 5L, 6L));
        assertThat(selectVersion(firstUserId)).isEqualTo(3L);
        assertThat(selectVersion(secondUserId)).isEqualTo(6L);
        assertThat(selectVersion(deletedRelationUserId)).isEqualTo(8L);
        assertThat(selectVersion(unrelatedUserId)).isEqualTo(11L);
        assertThat(selectVersion(deletedUserId)).isEqualTo(13L);
    }

    @Test
    void incrementUsersByRole_twiceInSameTransaction_returnsContiguousChangesAndUpdatesTwice() {
        UUID roleId = insertRole();
        UUID userId = insertUser(UUID.randomUUID(), 2L, NOT_DELETED);
        assignRole(userId, roleId, NOT_DELETED);

        List<List<AuthorizationVersionStore.VersionChange>> calls =
                new TransactionTemplate(transactionManager).execute(status -> List.of(
                        store.incrementUsersByRole(roleId),
                        store.incrementUsersByRole(roleId)));

        assertThat(calls).containsExactly(
                List.of(new AuthorizationVersionStore.VersionChange(userId, 2L, 3L)),
                List.of(new AuthorizationVersionStore.VersionChange(userId, 3L, 4L)));
        assertThat(selectVersion(userId)).isEqualTo(4L);
    }

    @Test
    void nullIdentifiers_returnEmptyWithoutMapperInteraction() {
        AuthorizationVersionMapper mapper = mock(AuthorizationVersionMapper.class);
        SysAuthorizationVersionStoreAdapter adapter =
                new SysAuthorizationVersionStoreAdapter(mapper);

        assertThat(adapter.incrementUser(null)).isEmpty();
        assertThat(adapter.incrementUsersByRole(null)).isEmpty();

        verifyNoInteractions(mapper);
    }

    @Test
    void incrementUser_rejectsNullMalformedAndMismatchedRows() {
        UUID requestedUserId = UUID.randomUUID();

        assertIncrementUserRejects(requestedUserId, null);
        assertIncrementUserRejects(requestedUserId, row(null, 1L, 2L));
        assertIncrementUserRejects(requestedUserId, row(requestedUserId, null, 2L));
        assertIncrementUserRejects(requestedUserId, row(requestedUserId, 1L, null));
        assertIncrementUserRejects(requestedUserId, row(requestedUserId, 0L, 1L));
        assertIncrementUserRejects(requestedUserId, row(requestedUserId, 4L, 6L));
        assertIncrementUserRejects(requestedUserId, row(UUID.randomUUID(), 4L, 5L));
    }

    @Test
    void adapter_rejectsNullResultAndDuplicateRoleRows() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        AuthorizationVersionMapper nullResultMapper = mock(AuthorizationVersionMapper.class);
        when(nullResultMapper.incrementUser(userId)).thenReturn(null);
        SysAuthorizationVersionStoreAdapter nullResultAdapter =
                new SysAuthorizationVersionStoreAdapter(nullResultMapper);

        assertThatThrownBy(() -> nullResultAdapter.incrementUser(userId))
                .isInstanceOf(IllegalStateException.class);

        AuthorizationVersionChangeRow duplicate = row(userId, 3L, 4L);
        AuthorizationVersionMapper duplicateMapper = mock(AuthorizationVersionMapper.class);
        when(duplicateMapper.incrementUsersByRole(roleId))
                .thenReturn(List.of(duplicate, duplicate));
        SysAuthorizationVersionStoreAdapter duplicateAdapter =
                new SysAuthorizationVersionStoreAdapter(duplicateMapper);

        assertThatThrownBy(() -> duplicateAdapter.incrementUsersByRole(roleId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void versionChange_enforcesIdentityAndContiguousPositiveVersions() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> new AuthorizationVersionStore.VersionChange(null, 1L, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionStore.VersionChange(userId, 0L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionStore.VersionChange(userId, 2L, 4L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationVersionStore.VersionChange(
                userId, Long.MAX_VALUE, Long.MIN_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertIncrementUserRejects(
            UUID requestedUserId,
            AuthorizationVersionChangeRow configuredRow) {
        AuthorizationVersionMapper mapper = mock(AuthorizationVersionMapper.class);
        List<AuthorizationVersionChangeRow> rows = configuredRow == null
                ? Collections.singletonList(null)
                : List.of(configuredRow);
        when(mapper.incrementUser(requestedUserId)).thenReturn(rows);
        SysAuthorizationVersionStoreAdapter adapter =
                new SysAuthorizationVersionStoreAdapter(mapper);

        assertThatThrownBy(() -> adapter.incrementUser(requestedUserId))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID insertUser(UUID userId, long authzVersion, int deleted) {
        String suffix = userId.toString().replace("-", "").substring(20);
        jdbcTemplate.update("""
                        INSERT INTO sys_user (
                            id, username, password, real_name, channel_code,
                            status, deleted, authz_version
                        ) VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                        """,
                userId,
                "version_user_" + suffix,
                "test-password-hash",
                "Authorization Version Test User",
                "v" + suffix,
                deleted,
                authzVersion);
        return userId;
    }

    private UUID insertRole() {
        UUID roleId = UUID.randomUUID();
        String suffix = roleId.toString().replace("-", "").substring(20);
        jdbcTemplate.update("""
                        INSERT INTO sys_role (
                            id, role_code, role_name, data_scope, status, deleted
                        ) VALUES (?, ?, ?, 1, 1, 0)
                        """,
                roleId,
                "version_role_" + suffix,
                "Authorization Version Test Role");
        return roleId;
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

    private long selectVersion(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT authz_version FROM sys_user WHERE id = ?",
                Long.class,
                userId);
    }

    private static AuthorizationVersionChangeRow row(
            UUID userId,
            Long previousVersion,
            Long currentVersion) {
        AuthorizationVersionChangeRow row = new AuthorizationVersionChangeRow();
        row.setUserId(userId);
        row.setPreviousVersion(previousVersion);
        row.setCurrentVersion(currentVersion);
        return row;
    }
}
