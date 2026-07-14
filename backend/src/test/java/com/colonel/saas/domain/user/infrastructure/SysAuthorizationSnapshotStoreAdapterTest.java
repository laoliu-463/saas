package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.mapper.AuthorizationSnapshotMapper;
import com.colonel.saas.mapper.projection.AuthorizationSnapshotRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysAuthorizationSnapshotStoreAdapterTest {

    @Mock
    private AuthorizationSnapshotMapper mapper;

    private SysAuthorizationSnapshotStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysAuthorizationSnapshotStoreAdapter(mapper);
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsEmptyList_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of());

        assertThat(adapter.loadActiveSnapshot(userId)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenMapperReturnsNull_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(null);

        assertThat(adapter.loadActiveSnapshot(userId)).isEmpty();
    }

    @Test
    void loadActiveSnapshot_whenActiveSubjectHasNoGrant_shouldReturnSubjectWithEmptyGrants() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AuthorizationSnapshotRow row = subjectRow(userId, deptId, 7L);
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of(row));

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
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of(row));

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
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of(row));

        AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

        assertThat(snapshot.grants()).singleElement().satisfies(grant ->
                assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY));
    }

    @Test
    void loadActiveSnapshot_whenScopeCodeIsUnknown_shouldMapScopeToDeny() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshotRow row = grantRow(
                userId, null, 1L, UUID.randomUUID(), "sample:read", "sample", true, "FUTURE");
        when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of(row));

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
                UUID.randomUUID(),
                UUID.randomUUID(),
                99L,
                UUID.randomUUID(),
                "sample:write",
                "sample",
                false,
                "ALL");
        when(mapper.findActiveSnapshotRows(firstUserId)).thenReturn(List.of(first, second));

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

    private static AuthorizationSnapshotRow subjectRow(UUID userId, UUID deptId, long authzVersion) {
        AuthorizationSnapshotRow row = new AuthorizationSnapshotRow();
        row.setUserId(userId);
        row.setDeptId(deptId);
        row.setAuthzVersion(authzVersion);
        return row;
    }

    private static AuthorizationSnapshotRow grantRow(
            UUID userId,
            UUID deptId,
            long authzVersion,
            UUID roleId,
            String permissionCode,
            String domainCode,
            boolean dataScopeRequired,
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
