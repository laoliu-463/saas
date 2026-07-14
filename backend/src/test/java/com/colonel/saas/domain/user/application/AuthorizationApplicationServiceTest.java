package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import com.colonel.saas.domain.user.policy.AuthorizationDecisionPolicy;
import com.colonel.saas.domain.user.port.AuthorizationSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationApplicationServiceTest {

    @Test
    void authorize_shouldDenyWhenSubjectIsNotActive() {
        UUID userId = UUID.randomUUID();
        AuthorizationApplicationService service = service((ignoredUser, ignoredVersion) -> Optional.empty());

        AuthorizationDecision decision = service.authorize(principal(userId, 7L), "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.SUBJECT_NOT_ACTIVE);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationScope.class, names = {"SELF", "GROUP", "ALL"})
    void authorize_shouldAllowActiveSubjectWithDepartmentAndGrantedScope(
            AuthorizationScope scope) {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                UUID.randomUUID(),
                List.of(grant("sample:read", true, scope)));
        AuthorizationApplicationService service = service(
                (ignoredUser, ignoredVersion) -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(principal(userId, 7L), "sample:read");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isEqualTo("sample");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.GRANTED);
        assertThat(decision.scope()).isEqualTo(scope);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationScope.class, names = {"GROUP", "ALL"})
    void authorize_shouldDenyScopedGrantWhenSubjectDepartmentIsMissing(
            AuthorizationScope scope) {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                null,
                List.of(grant("sample:read", true, scope)));
        AuthorizationApplicationService service = service(
                (ignoredUser, ignoredVersion) -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(principal(userId, 7L), "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.DOMAIN_SCOPE_MISSING);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void authorize_shouldDenyUnscopedGrantWhenSubjectDepartmentIsMissing() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                null,
                List.of(grant("system:login", false, AuthorizationScope.DENY)));
        AuthorizationApplicationService service = service(
                (ignoredUser, ignoredVersion) -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(principal(userId, 7L), "system:login");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("system:login");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.DOMAIN_SCOPE_MISSING);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void authorize_shouldDenyWhenRequestedPermissionIsNotGranted() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                List.of(grant("product:read", AuthorizationScope.ALL)));
        AuthorizationApplicationService service = service(
                (ignoredUser, ignoredVersion) -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(principal(userId, 7L), "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.PERMISSION_NOT_GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void authorize_shouldDenyNullPrincipalWithoutLoadingStore() {
        AtomicBoolean storeCalled = new AtomicBoolean();
        AuthorizationApplicationService service = service((userId, authzVersion) -> {
            storeCalled.set(true);
            return Optional.empty();
        });

        AuthorizationDecision decision = service.authorize(null, "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.SUBJECT_NOT_ACTIVE);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
        assertThat(storeCalled).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "Sample:read",
            "sampleread",
            "sample:read:detail"
    })
    void authorize_shouldRejectMalformedPermissionBeforeSubjectLookup(String rawPermissionCode) {
        AuthorizationApplicationService service = service((userId, authzVersion) -> {
            throw new AssertionError("store must not be called for malformed permission");
        });

        assertThatThrownBy(() -> service.authorize(null, rawPermissionCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permissionCode must use canonical resource:action syntax");
    }

    @Test
    void authorize_shouldPropagateStoreFailureUnchanged() {
        UUID userId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("authorization store unavailable");
        AuthorizationApplicationService service = service((ignoredUser, ignoredVersion) -> {
            throw failure;
        });

        assertThatThrownBy(() -> service.authorize(principal(userId, 7L), "sample:read"))
                .isSameAs(failure);
    }

    @Test
    void authorize_shouldLoadSnapshotWithPrincipalUserAndExactVersion() {
        UUID userId = UUID.randomUUID();
        AtomicReference<UUID> loadedUserId = new AtomicReference<>();
        AtomicLong loadedVersion = new AtomicLong();
        AuthorizationApplicationService service = service((requestedUserId, authzVersion) -> {
            loadedUserId.set(requestedUserId);
            loadedVersion.set(authzVersion);
            return Optional.empty();
        });

        service.authorize(principal(userId, 23L), "sample:read");

        assertThat(loadedUserId).hasValue(userId);
        assertThat(loadedVersion).hasValue(23L);
    }

    private AuthorizationApplicationService service(AuthorizationSnapshotStore store) {
        return new AuthorizationApplicationService(
                store,
                new AuthorizationDecisionPolicy());
    }

    private AuthorizationPrincipal principal(UUID userId, long authzVersion) {
        return new AuthorizationPrincipal(
                userId,
                UUID.randomUUID(),
                "alice",
                authzVersion,
                false);
    }

    private AuthorizationSnapshot snapshot(
            UUID userId,
            List<GrantedRolePermission> grants) {
        return snapshot(userId, UUID.randomUUID(), grants);
    }

    private AuthorizationSnapshot snapshot(
            UUID userId,
            UUID deptId,
            List<GrantedRolePermission> grants) {
        return new AuthorizationSnapshot(
                new AuthorizationSubject(userId, deptId, 7L),
                grants);
    }

    private GrantedRolePermission grant(
            String permissionCode,
            AuthorizationScope scope) {
        return grant(permissionCode, true, scope);
    }

    private GrantedRolePermission grant(
            String permissionCode,
            boolean dataScopeRequired,
            AuthorizationScope scope) {
        return new GrantedRolePermission(
                UUID.randomUUID(),
                new PermissionCode(permissionCode),
                permissionCode.substring(0, permissionCode.indexOf(':')),
                dataScopeRequired,
                scope);
    }
}
