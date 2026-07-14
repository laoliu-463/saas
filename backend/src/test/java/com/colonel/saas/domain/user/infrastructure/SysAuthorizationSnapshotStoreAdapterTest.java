package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.mapper.projection.AuthorizationSnapshotRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SysAuthorizationSnapshotStoreAdapterTest {

    private List<AuthorizationSnapshotRow> configuredRows;
    private SysAuthorizationSnapshotStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysAuthorizationSnapshotStoreAdapter(
                (userId, authzVersion) -> configuredRows);
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsEmptyList_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of();

        assertThat(adapter.loadActiveSnapshot(userId, 7L)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsNull_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        configuredRows = null;

        assertThat(adapter.loadActiveSnapshot(userId, 7L)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenInputIsInvalid_shouldFailClosedWithoutQueryingMapper() {
        adapter = new SysAuthorizationSnapshotStoreAdapter((userId, authzVersion) -> {
            throw new AssertionError("mapper must not be called for invalid input");
        });

        assertThat(adapter.loadActiveSnapshot(null, 1L)).isEmpty();
        assertThat(adapter.loadActiveSnapshot(UUID.randomUUID(), 0L)).isEmpty();
        assertThat(adapter.loadActiveSnapshot(UUID.randomUUID(), -1L)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenActiveSubjectHasNoGrant_shouldReturnSubjectWithEmptyGrants() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow row = subjectRow(userId, deptId, 7L);
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId, 7L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(userId);
        assertThat(snapshot.subject().deptId()).isEqualTo(deptId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(7L);
        assertThat(snapshot.grants()).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenActiveGrantRowIsComplete_shouldMapSubjectAndGrant() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        AuthorizationSnapshotRow row = grantRow(
                userId, deptId, 11L, roleId, "sample:read", "sample", true, "GROUP");
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId, 11L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(userId);
        assertThat(snapshot.subject().deptId()).isEqualTo(deptId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(11L);
        assertThat(snapshot.grants()).singleElement().satisfies(grant -> {
            assertThat(grant.roleId()).isEqualTo(roleId);
            assertThat(grant.permission().value()).isEqualTo("sample:read");
            assertThat(grant.domainCode()).isEqualTo("sample");
            assertThat(grant.dataScopeRequired()).isTrue();
            assertThat(grant.scope()).isEqualTo(AuthorizationScope.GROUP);
        });
    }

    @Test
    void loadActiveSnapshot_whenScopeCodeIsNull_shouldMapScopeToDeny() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow row = grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", true, null);
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId, 1L).orElseThrow();

        assertThat(snapshot.grants()).singleElement().satisfies(grant ->
                assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY));
    }

    @Test
    void loadActiveSnapshot_whenScopeCodeIsUnknown_shouldMapScopeToDeny() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow row = grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", true, "FUTURE");
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId, 1L).orElseThrow();

        assertThat(snapshot.grants()).singleElement().satisfies(grant ->
                assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY));
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsMultipleRows_shouldUseFirstSubjectAndMapAllGrants() {
        UUID firstUserId = UUID.randomUUID();
        UUID firstDeptId = UUID.randomUUID();
        AuthorizationSnapshotRow first = grantRow(
                firstUserId,
                firstDeptId,
                13L,
                UUID.randomUUID(),
                "order:read",
                "order",
                true,
                "SELF");
        AuthorizationSnapshotRow second = grantRow(
                firstUserId,
                firstDeptId,
                13L,
                UUID.randomUUID(),
                "sample:write",
                "sample",
                false,
                "ALL");
        configuredRows = List.of(first, second);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(firstUserId, 13L).orElseThrow();

        assertThat(snapshot.subject().userId()).isEqualTo(firstUserId);
        assertThat(snapshot.subject().deptId()).isEqualTo(firstDeptId);
        assertThat(snapshot.subject().authzVersion()).isEqualTo(13L);
        assertThat(snapshot.grants())
                .extracting(grant -> grant.permission().value())
                .containsExactly("order:read", "sample:write");
        assertThat(snapshot.grants())
                .extracting(grant -> grant.scope())
                .containsExactly(AuthorizationScope.SELF, AuthorizationScope.ALL);
    }

    @Test
    void loadActiveSnapshot_whenRequestedUserDiffersFromFirstRow_shouldRejectSnapshot() {
        UUID requestedUserId = UUID.randomUUID();
        configuredRows = List.of(subjectRow(UUID.randomUUID(), UUID.randomUUID(), 1L));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(requestedUserId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowUserIdIsNull_shouldRejectSnapshot() {
        UUID requestedUserId = UUID.randomUUID();
        AuthorizationSnapshotRow invalidRow = subjectRow(null, null, 1L);
        adapter = new SysAuthorizationSnapshotStoreAdapter((userId, authzVersion) -> {
            if (requestedUserId.equals(userId) && authzVersion == 1L) {
                return List.of(invalidRow);
            }
            return List.of();
        });

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(requestedUserId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowAuthorizationVersionIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(subjectRow(userId, null, null));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentUser_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, deptId, 1L);
        AuthorizationSnapshotRow second = subjectRow(UUID.randomUUID(), deptId, 1L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentDepartment_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, UUID.randomUUID(), 1L);
        AuthorizationSnapshotRow second = subjectRow(userId, UUID.randomUUID(), 1L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentAuthorizationVersion_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, deptId, 1L);
        AuthorizationSnapshotRow second = subjectRow(userId, deptId, 2L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantRoleIdIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, null, "sample:read", "sample", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDataScopeRequiredIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", null, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDomainCodeIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", null, true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDomainCodeIsBlank_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", " ", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = java.util.Collections.singletonList(null);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = java.util.Arrays.asList(subjectRow(userId, null, 1L), null);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenMapperFails_shouldPropagateSameException() {
        UUID userId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        adapter = new SysAuthorizationSnapshotStoreAdapter((ignoredUser, ignoredVersion) -> {
            throw failure;
        });

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isSameAs(failure);
    }

    @Test
    void loadActiveSnapshot_whenPermissionCodeIsMalformed_shouldPropagateValidationFailure() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "MALFORMED", "sample", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadActiveSnapshot_shouldPassUserAndExpectedVersionToMapper() {
        UUID userId = UUID.randomUUID();
        AtomicReference<UUID> mappedUserId = new AtomicReference<>();
        AtomicLong mappedVersion = new AtomicLong();
        adapter = new SysAuthorizationSnapshotStoreAdapter((requestedUserId, authzVersion) -> {
            mappedUserId.set(requestedUserId);
            mappedVersion.set(authzVersion);
            return List.of();
        });

        assertThat(adapter.loadActiveSnapshot(userId, 19L)).isEmpty();
        assertThat(mappedUserId).hasValue(userId);
        assertThat(mappedVersion).hasValue(19L);
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsDifferentVersion_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(subjectRow(userId, null, 8L));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authorization snapshot version does not match request");
    }

    private static AuthorizationSnapshotRow subjectRow(UUID userId, UUID deptId, Long authzVersion) {
        AuthorizationSnapshotRow row = new AuthorizationSnapshotRow();
        row.setUserId(userId);
        row.setDeptId(deptId);
        row.setAuthzVersion(authzVersion);
        return row;
    }

    private static AuthorizationSnapshotRow grantRow(
            UUID userId,
            UUID deptId,
            Long authzVersion,
            UUID roleId,
            String permissionCode,
            String domainCode,
            Boolean dataScopeRequired,
            String scopeCode) {
        AuthorizationSnapshotRow row = subjectRow(userId, deptId, authzVersion);
        row.setRoleId(roleId);
        row.setPermissionCode(permissionCode);
        row.setDomainCode(domainCode);
        row.setDataScopeRequired(dataScopeRequired);
        row.setScopeCode(scopeCode);
        return row;
    }
}
