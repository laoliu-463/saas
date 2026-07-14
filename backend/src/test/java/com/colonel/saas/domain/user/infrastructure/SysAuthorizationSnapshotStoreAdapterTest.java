package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.mapper.projection.AuthorizationSnapshotRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SysAuthorizationSnapshotStoreAdapterTest {

    private List<AuthorizationSnapshotRow> configuredRows;
    private SysAuthorizationSnapshotStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysAuthorizationSnapshotStoreAdapter(userId -> configuredRows);
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsEmptyList_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of();

        assertThat(adapter.loadActiveSnapshot(userId)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsNull_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        configuredRows = null;

        assertThat(adapter.loadActiveSnapshot(userId)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenActiveSubjectHasNoGrant_shouldReturnSubjectWithEmptyGrants() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow row = subjectRow(userId, deptId, 7L);
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

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

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

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

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

        assertThat(snapshot.grants()).singleElement().satisfies(grant ->
                assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY));
    }

    @Test
    void loadActiveSnapshot_whenScopeCodeIsUnknown_shouldMapScopeToDeny() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow row = grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", true, "FUTURE");
        configuredRows = List.of(row);

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

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

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(firstUserId).orElseThrow();

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

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(requestedUserId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowUserIdIsNull_shouldRejectSnapshot() {
        configuredRows = List.of(subjectRow(null, null, 1L));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowAuthorizationVersionIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(subjectRow(userId, null, null));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentUser_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, deptId, 1L);
        AuthorizationSnapshotRow second = subjectRow(UUID.randomUUID(), deptId, 1L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentDepartment_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, UUID.randomUUID(), 1L);
        AuthorizationSnapshotRow second = subjectRow(userId, UUID.randomUUID(), 1L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowHasDifferentAuthorizationVersion_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow first = subjectRow(userId, deptId, 1L);
        AuthorizationSnapshotRow second = subjectRow(userId, deptId, 2L);
        configuredRows = List.of(first, second);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantRoleIdIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, null, "sample:read", "sample", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDataScopeRequiredIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", null, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDomainCodeIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", null, true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenGrantDomainCodeIsBlank_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", " ", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenFirstRowIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = java.util.Collections.singletonList(null);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenLaterRowIsNull_shouldRejectSnapshot() {
        UUID userId = UUID.randomUUID();
        configuredRows = java.util.Arrays.asList(subjectRow(userId, null, 1L), null);

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadActiveSnapshot_whenMapperFails_shouldPropagateSameException() {
        UUID userId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        adapter = new SysAuthorizationSnapshotStoreAdapter(ignored -> {
            throw failure;
        });

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isSameAs(failure);
    }

    @Test
    void loadActiveSnapshot_whenPermissionCodeIsMalformed_shouldPropagateValidationFailure() {
        UUID userId = UUID.randomUUID();
        configuredRows = List.of(grantRow(
                userId, null, 1L, UUID.randomUUID(), "MALFORMED", "sample", true, "SELF"));

        assertThatThrownBy(() -> adapter.loadActiveSnapshot(userId))
                .isInstanceOf(IllegalArgumentException.class);
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
