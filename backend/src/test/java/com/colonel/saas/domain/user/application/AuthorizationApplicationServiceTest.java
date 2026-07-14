package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationApplicationServiceTest {

    @Test
    void authorize_shouldDenyWhenSubjectIsNotActive() {
        UUID userId = UUID.randomUUID();
        AuthorizationApplicationService service = service(ignored -> Optional.empty());

        AuthorizationDecision decision = service.authorize(userId, "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.SUBJECT_NOT_ACTIVE);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void authorize_shouldAllowActiveSubjectWithGrantedSelfScope() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                List.of(grant("sample:read", AuthorizationScope.SELF)));
        AuthorizationApplicationService service = service(
                ignored -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(userId, "sample:read");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isEqualTo("sample");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.SELF);
    }

    @Test
    void authorize_shouldDenyWhenRequestedPermissionIsNotGranted() {
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
                userId,
                List.of(grant("product:read", AuthorizationScope.ALL)));
        AuthorizationApplicationService service = service(
                ignored -> Optional.of(snapshot));

        AuthorizationDecision decision = service.authorize(userId, "sample:read");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.PERMISSION_NOT_GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void authorize_shouldDenyNullUserWithoutLoadingStore() {
        AtomicBoolean storeCalled = new AtomicBoolean();
        AuthorizationApplicationService service = service(userId -> {
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
        AuthorizationApplicationService service = service(userId -> {
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
        AuthorizationApplicationService service = service(ignored -> {
            throw failure;
        });

        assertThatThrownBy(() -> service.authorize(userId, "sample:read"))
                .isSameAs(failure);
    }

    private AuthorizationApplicationService service(AuthorizationSnapshotStore store) {
        return new AuthorizationApplicationService(
                store,
                new AuthorizationDecisionPolicy());
    }

    private AuthorizationSnapshot snapshot(
            UUID userId,
            List<GrantedRolePermission> grants) {
        return new AuthorizationSnapshot(
                new AuthorizationSubject(userId, UUID.randomUUID(), 7L),
                grants);
    }

    private GrantedRolePermission grant(
            String permissionCode,
            AuthorizationScope scope) {
        return new GrantedRolePermission(
                UUID.randomUUID(),
                new PermissionCode(permissionCode),
                permissionCode.substring(0, permissionCode.indexOf(':')),
                true,
                scope);
    }
}
