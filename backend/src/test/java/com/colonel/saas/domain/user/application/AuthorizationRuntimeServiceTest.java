package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationComparison;
import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import com.colonel.saas.domain.user.infrastructure.AuthorizationDifferenceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationRuntimeServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static final String DOMAIN = "sample";
    private static final String PERMISSION = "sample:approve";

    @Mock
    private AuthorizationFacade authorizationFacade;

    @Mock
    private AuthorizationRuntimeProperties properties;

    @Mock
    private AuthorizationDifferenceLogger differenceLogger;

    private AuthorizationRuntimeService service;
    private AuthorizationPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new AuthorizationRuntimeService(
                authorizationFacade,
                properties,
                differenceLogger);
        principal = new AuthorizationPrincipal(USER_ID, null, "alice", 6L, false);
    }

    @ParameterizedTest
    @EnumSource(value = LegacyDecision.class)
    void legacy_usesOnlyLegacyDecisionAndDoesNotLog(LegacyDecision legacy) {
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.LEGACY);

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, legacy.allowed);

        assertThat(decision.userId()).isEqualTo(USER_ID);
        assertThat(decision.mode()).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(decision.legacyAllowed()).isEqualTo(legacy.allowed);
        assertThat(decision.newDecision()).isNull();
        assertThat(decision.effectiveAllowed()).isEqualTo(legacy.allowed);
        assertThat(decision.comparison()).isEqualTo(AuthorizationComparison.NOT_EVALUATED);
        verifyNoInteractions(authorizationFacade, differenceLogger);
    }

    @ParameterizedTest
    @MethodSource("authorizationTruthTable")
    void shadow_keepsLegacyDecisionClassifiesEveryOutcomeAndLogs(
            boolean legacyAllowed,
            boolean newAllowed,
            AuthorizationComparison expectedComparison) {
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.SHADOW);
        when(authorizationFacade.authorize(principal, PERMISSION))
                .thenReturn(newDecision(newAllowed));

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, legacyAllowed);

        assertThat(decision.mode()).isEqualTo(AuthorizationRuntimeMode.SHADOW);
        assertThat(decision.legacyAllowed()).isEqualTo(legacyAllowed);
        assertThat(decision.newDecision().allowed()).isEqualTo(newAllowed);
        assertThat(decision.effectiveAllowed()).isEqualTo(legacyAllowed);
        assertThat(decision.comparison()).isEqualTo(expectedComparison);
        verify(differenceLogger).log(decision);
    }

    @ParameterizedTest
    @MethodSource("authorizationTruthTable")
    void enforce_usesOnlyNewDecisionAndNeverOrsWithLegacy(
            boolean legacyAllowed,
            boolean newAllowed,
            AuthorizationComparison expectedComparison) {
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.ENFORCE);
        when(authorizationFacade.authorize(principal, PERMISSION))
                .thenReturn(newDecision(newAllowed));

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, legacyAllowed);

        assertThat(decision.mode()).isEqualTo(AuthorizationRuntimeMode.ENFORCE);
        assertThat(decision.legacyAllowed()).isEqualTo(legacyAllowed);
        assertThat(decision.newDecision().allowed()).isEqualTo(newAllowed);
        assertThat(decision.effectiveAllowed()).isEqualTo(newAllowed);
        assertThat(decision.comparison()).isEqualTo(expectedComparison);
        verify(differenceLogger).log(decision);
    }

    @Test
    void require_mapsEffectiveDenyToForbidden() {
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.ENFORCE);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenReturn(newDecision(false));

        assertThatThrownBy(() -> service.require(principal, DOMAIN, PERMISSION, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("无权限访问该接口");
    }

    @ParameterizedTest
    @EnumSource(value = LegacyDecision.class)
    void shadow_newAuthorizationUnavailableKeepsLegacyAndLogs(LegacyDecision legacy) {
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.SHADOW);
        when(authorizationFacade.authorize(principal, PERMISSION))
                .thenThrow(new AuthorizationUnavailableException(
                        new IllegalStateException("sensitive store failure")));

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, legacy.allowed);

        assertThat(decision.legacyAllowed()).isEqualTo(legacy.allowed);
        assertThat(decision.newDecision()).isNull();
        assertThat(decision.effectiveAllowed()).isEqualTo(legacy.allowed);
        assertThat(decision.comparison()).isEqualTo(AuthorizationComparison.NEW_UNAVAILABLE);
        verify(differenceLogger).log(decision);
    }

    @Test
    void enforce_newAuthorizationUnavailablePropagatesSameExceptionWithout403OrLog() {
        AuthorizationUnavailableException failure = new AuthorizationUnavailableException(
                new IllegalStateException("sensitive store failure"));
        when(properties.modeFor(DOMAIN)).thenReturn(AuthorizationRuntimeMode.ENFORCE);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenThrow(failure);

        assertThatThrownBy(() -> service.require(principal, DOMAIN, PERMISSION, true))
                .isSameAs(failure)
                .isNotInstanceOf(ForbiddenException.class);
        verifyNoInteractions(differenceLogger);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationRuntimeMode.class, names = {"SHADOW", "ENFORCE"})
    void nonAvailabilityFailurePropagatesUnchanged(AuthorizationRuntimeMode mode) {
        IllegalStateException failure = new IllegalStateException("programming failure");
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, PERMISSION, true))
                .isSameAs(failure);
        verifyNoInteractions(differenceLogger);
    }

    private static AuthorizationDecision newDecision(boolean allowed) {
        PermissionCode permission = new PermissionCode(PERMISSION);
        return allowed
                ? AuthorizationDecision.allow(permission, DOMAIN, AuthorizationScope.GROUP)
                : AuthorizationDecision.deny(
                        permission,
                        DOMAIN,
                        AuthorizationReason.PERMISSION_NOT_GRANTED);
    }

    private static Stream<Arguments> authorizationTruthTable() {
        return Stream.of(
                Arguments.of(true, true, AuthorizationComparison.BOTH_ALLOW),
                Arguments.of(false, false, AuthorizationComparison.BOTH_DENY),
                Arguments.of(true, false, AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                Arguments.of(false, true, AuthorizationComparison.OLD_DENY_NEW_ALLOW));
    }

    private enum LegacyDecision {
        ALLOW(true),
        DENY(false);

        private final boolean allowed;

        LegacyDecision(boolean allowed) {
            this.allowed = allowed;
        }
    }
}
