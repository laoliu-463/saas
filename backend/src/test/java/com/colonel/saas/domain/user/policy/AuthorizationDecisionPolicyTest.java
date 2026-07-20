package com.colonel.saas.domain.user.policy;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.AuthorizationSubject;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationDecisionPolicyTest {

    private final AuthorizationDecisionPolicy policy = new AuthorizationDecisionPolicy();

    @Test
    void decide_shouldDenyWhenPermissionIsNotGranted() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"), List.of());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissionCode()).isEqualTo("sample:read");
        assertThat(decision.domainCode()).isNull();
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.PERMISSION_NOT_GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void decide_shouldDenyScopedPermissionWhenEveryGrantLacksDomainScope() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(grant("sample:read", "sample", true, AuthorizationScope.DENY)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.domainCode()).isEqualTo("sample");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.DOMAIN_SCOPE_MISSING);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    @Test
    void decide_shouldDenyWhenSubjectDepartmentContextIsMissing() {
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(
                new AuthorizationSubject(UUID.randomUUID(), null, 1L),
                List.of(grant("sample:read", "sample", true, AuthorizationScope.GROUP)));

        AuthorizationDecision decision = policy.decide(
                new PermissionCode("sample:read"),
                snapshot);

        assertDomainScopeDenied(decision, "sample");
    }

    @Test
    void decide_shouldMergeOnlyRolesThatGrantTheRequestedPermission() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(
                        grant("sample:read", "sample", true, AuthorizationScope.GROUP),
                        grant("product:read", "product", true, AuthorizationScope.ALL)));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.domainCode()).isEqualTo("sample");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.GROUP);
    }

    @Test
    void decide_shouldAllowUnscopedPermissionWithoutRoleDomainScope() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("system:login"),
                List.of(grant("system:login", "system", false, AuthorizationScope.DENY)));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.domainCode()).isEqualTo("system");
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.GRANTED);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.ALL);
    }

    @Test
    void decide_shouldDenyWhenMatchingGrantsUseDifferentDomains() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(
                        grant("sample:read", "sample", true, AuthorizationScope.SELF),
                        grant("sample:read", "product", true, AuthorizationScope.ALL)));

        assertDomainScopeDenied(decision, "sample");
    }

    @Test
    void decide_shouldDenyWhenMatchingGrantsDisagreeOnDataScopeRequirement() {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(
                        grant("sample:read", "sample", true, AuthorizationScope.ALL),
                        grant("sample:read", "sample", false, AuthorizationScope.DENY)));

        assertDomainScopeDenied(decision, "sample");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void decide_shouldDenyWhenMatchingGrantHasInvalidDomain(String domainCode) {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(grant("sample:read", domainCode, true, AuthorizationScope.SELF)));

        assertDomainScopeDenied(decision, null);
    }

    @Test
    void decide_shouldDenyWhenScopedMatchingGrantHasNullScope() {
        AtomicReference<AuthorizationDecision> decision = new AtomicReference<>();

        assertThatCode(() -> decision.set(decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(grant("sample:read", "sample", true, null)))))
                .doesNotThrowAnyException();
        assertDomainScopeDenied(decision.get(), "sample");
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("scopeRankCases")
    void decide_shouldUseWidestScopeAcrossMatchingGrants(
            AuthorizationScope first,
            AuthorizationScope second,
            AuthorizationScope expected) {
        AuthorizationDecision decision = decideWithDepartment(
                new PermissionCode("sample:read"),
                List.of(
                        grant("sample:read", "sample", true, first),
                        grant("sample:read", "sample", true, second)));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.scope()).isEqualTo(expected);
    }

    @Test
    void decide_shouldTreatSnapshotNullGrantListAsEmpty() {
        PermissionCode permission = new PermissionCode("sample:read");
        AuthorizationSubject subject = subjectWithDepartment();

        assertThat(policy.decide(permission, new AuthorizationSnapshot(subject, null)))
                .isEqualTo(policy.decide(permission, new AuthorizationSnapshot(subject, List.of())));
    }

    @ParameterizedTest(name = "allowed={0}, scope={1}, reason={2}")
    @MethodSource("contradictoryDecisionCases")
    void authorizationDecision_shouldRejectContradictoryState(
            boolean allowed,
            AuthorizationScope scope,
            AuthorizationReason reason) {
        assertThatThrownBy(() -> new AuthorizationDecision(
                allowed,
                "sample:read",
                "sample",
                scope,
                reason))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authorizationDecision_shouldRejectNullRequiredFields() {
        assertThatThrownBy(() -> new AuthorizationDecision(
                true,
                null,
                "sample",
                AuthorizationScope.SELF,
                AuthorizationReason.GRANTED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthorizationDecision(
                true,
                "sample:read",
                "sample",
                null,
                AuthorizationReason.GRANTED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthorizationDecision(
                false,
                "sample:read",
                "sample",
                AuthorizationScope.DENY,
                null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void authorizationDecision_factoriesShouldProduceValidState() {
        PermissionCode permission = new PermissionCode("sample:read");

        assertThatCode(() -> AuthorizationDecision.allow(
                permission,
                "sample",
                AuthorizationScope.SELF))
                .doesNotThrowAnyException();
        assertThatCode(() -> AuthorizationDecision.deny(
                permission,
                "sample",
                AuthorizationReason.DOMAIN_SCOPE_MISSING))
                .doesNotThrowAnyException();
    }

    @Test
    void permissionCode_shouldAcceptCanonicalLowercaseResourceAction() {
        PermissionCode permission = new PermissionCode("sample-item2:read-detail3");

        assertThat(permission.value()).isEqualTo("sample-item2:read-detail3");
    }

    @Test
    void permissionCode_shouldRejectNonCanonicalValues() {
        for (String value : new String[]{
                null,
                "",
                " ",
                "Sample:read",
                "sample:Read",
                "sampleread",
                "sample:read:detail"
        }) {
            assertThatThrownBy(() -> new PermissionCode(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("permissionCode must use canonical resource:action syntax");
        }
    }

    @Test
    void authorizationSubject_shouldAllowMissingDepartment() {
        UUID userId = UUID.randomUUID();

        AuthorizationSubject subject = new AuthorizationSubject(userId, null, 1L);

        assertThat(subject.userId()).isEqualTo(userId);
        assertThat(subject.deptId()).isNull();
        assertThat(subject.authzVersion()).isEqualTo(1L);
    }

    @Test
    void authorizationSubject_shouldRejectMissingUser() {
        assertThatThrownBy(() -> new AuthorizationSubject(null, null, 1L))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");
    }

    @Test
    void authorizationSubject_shouldRejectNonPositiveVersion() {
        assertThatThrownBy(() -> new AuthorizationSubject(UUID.randomUUID(), null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authzVersion must be positive");
    }

    @Test
    void authorizationSnapshot_shouldTreatNullGrantsAsEmpty() {
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(subject(), null);

        assertThat(snapshot.grants()).isEmpty();
    }

    @Test
    void authorizationSnapshot_shouldDefensivelyCopyGrants() {
        List<GrantedRolePermission> grants = new ArrayList<>();
        grants.add(grant("sample:read", "sample", true, AuthorizationScope.SELF));

        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(subject(), grants);
        grants.clear();

        assertThat(snapshot.grants()).hasSize(1);
        assertThatThrownBy(() -> snapshot.grants().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void authorizationSnapshot_shouldRejectMissingSubject() {
        assertThatThrownBy(() -> new AuthorizationSnapshot(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("subject");
    }

    private static Stream<Arguments> scopeRankCases() {
        return Stream.of(
                Arguments.of(AuthorizationScope.DENY, AuthorizationScope.SELF, AuthorizationScope.SELF),
                Arguments.of(AuthorizationScope.SELF, AuthorizationScope.GROUP, AuthorizationScope.GROUP),
                Arguments.of(AuthorizationScope.GROUP, AuthorizationScope.ALL, AuthorizationScope.ALL));
    }

    private static Stream<Arguments> contradictoryDecisionCases() {
        return Stream.of(
                Arguments.of(true, AuthorizationScope.DENY, AuthorizationReason.GRANTED),
                Arguments.of(true, AuthorizationScope.SELF, AuthorizationReason.PERMISSION_NOT_GRANTED),
                Arguments.of(false, AuthorizationScope.SELF, AuthorizationReason.PERMISSION_NOT_GRANTED),
                Arguments.of(false, AuthorizationScope.DENY, AuthorizationReason.GRANTED));
    }

    private void assertDomainScopeDenied(
            AuthorizationDecision decision,
            String expectedDomainCode) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.domainCode()).isEqualTo(expectedDomainCode);
        assertThat(decision.reason()).isEqualTo(AuthorizationReason.DOMAIN_SCOPE_MISSING);
        assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
    }

    private AuthorizationDecision decideWithDepartment(
            PermissionCode permission,
            List<GrantedRolePermission> grants) {
        return policy.decide(
                permission,
                new AuthorizationSnapshot(subjectWithDepartment(), grants));
    }

    private GrantedRolePermission grant(
            String permission,
            String domainCode,
            boolean dataScopeRequired,
            AuthorizationScope scope) {
        return new GrantedRolePermission(
                UUID.randomUUID(),
                new PermissionCode(permission),
                domainCode,
                dataScopeRequired,
                scope);
    }

    private AuthorizationSubject subject() {
        return new AuthorizationSubject(UUID.randomUUID(), null, 1L);
    }

    private AuthorizationSubject subjectWithDepartment() {
        return new AuthorizationSubject(UUID.randomUUID(), UUID.randomUUID(), 1L);
    }
}
