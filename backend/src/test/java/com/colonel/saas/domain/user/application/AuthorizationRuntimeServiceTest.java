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
import com.colonel.saas.domain.user.port.AuthorizationDifferenceSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private AuthorizationDifferenceSink differenceSink;

    private AuthorizationRuntimeService service;
    private AuthorizationPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new AuthorizationRuntimeService(
                authorizationFacade,
                properties,
                differenceSink);
        principal = new AuthorizationPrincipal(USER_ID, null, "alice", 6L, false);
    }

    @Test
    void nullPrincipalIsRejectedBeforeCallingCollaborators() {
        assertThatThrownBy(() -> service.evaluate(null, DOMAIN, PERMISSION, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("principal");

        verifyNoInteractions(properties, authorizationFacade, differenceSink);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "unknown", "Sample", "sample\r\n"})
    void nonCanonicalOrMismatchedDomainIsRejectedBeforeCallingCollaborators(String domainCode) {
        assertThatThrownBy(() -> service.evaluate(principal, domainCode, PERMISSION, true))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(properties, authorizationFacade, differenceSink);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "sample", "unknown", "sample:approve\r\n"})
    void nonCanonicalPermissionIsRejectedBeforeCallingCollaborators(String permissionCode) {
        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, permissionCode, true))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(properties, authorizationFacade, differenceSink);
    }

    @Test
    void nullRuntimeModeFailsClosedBeforeCallingAuthorizationOrSink() {
        when(properties.modeFor(DOMAIN)).thenReturn(null);

        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, PERMISSION, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mode");

        verifyNoInteractions(authorizationFacade, differenceSink);
    }

    @Test
    void runtimeServiceDependsOnDifferenceSinkPort() {
        Class<?>[] constructorTypes = Arrays.stream(
                        AuthorizationRuntimeService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toArray(Class<?>[]::new);

        assertThat(constructorTypes)
                .contains(AuthorizationDifferenceSink.class)
                .doesNotContain(AuthorizationDifferenceLogger.class);
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
        verifyNoInteractions(authorizationFacade, differenceSink);
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
        verify(differenceSink).log(decision);
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
        verify(differenceSink).log(decision);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationRuntimeMode.class, names = {"SHADOW", "ENFORCE"})
    void nullFacadeDecisionFailsClosedWithoutCallingSink(AuthorizationRuntimeMode mode) {
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenReturn(null);

        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, PERMISSION, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decision");
        verifyNoInteractions(differenceSink);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationRuntimeMode.class, names = {"SHADOW", "ENFORCE"})
    void mismatchedFacadePermissionFailsClosedWithoutCallingSink(AuthorizationRuntimeMode mode) {
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION))
                .thenReturn(decisionFor("sample:read", true));

        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, PERMISSION, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permission");
        verifyNoInteractions(differenceSink);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationRuntimeMode.class, names = {"SHADOW", "ENFORCE"})
    void nullFacadeDecisionDomainRemainsValidForInactiveSubject(AuthorizationRuntimeMode mode) {
        AuthorizationDecision inactive = new AuthorizationDecision(
                false,
                PERMISSION,
                null,
                AuthorizationScope.DENY,
                AuthorizationReason.SUBJECT_NOT_ACTIVE);
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenReturn(inactive);

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, true);

        assertThat(decision.newDecision()).isSameAs(inactive);
        assertThat(decision.comparison()).isEqualTo(AuthorizationComparison.OLD_ALLOW_NEW_DENY);
        assertThat(decision.effectiveAllowed())
                .isEqualTo(mode == AuthorizationRuntimeMode.SHADOW);
        verify(differenceSink).log(decision);
    }

    @ParameterizedTest(name = "{0} sink failure in {1}")
    @MethodSource("sinkFailureCases")
    void sinkRuntimeFailureNeverChangesAValidDecision(
            String failureType,
            AuthorizationRuntimeMode mode,
            RuntimeException failure) {
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenReturn(newDecision(false));
        doThrow(failure).when(differenceSink).log(any(AuthorizationRuntimeDecision.class));

        AuthorizationRuntimeDecision decision = service.evaluate(
                principal, DOMAIN, PERMISSION, true);

        assertThat(decision.comparison()).isEqualTo(AuthorizationComparison.OLD_ALLOW_NEW_DENY);
        assertThat(decision.effectiveAllowed())
                .isEqualTo(mode == AuthorizationRuntimeMode.SHADOW);
        verify(differenceSink).log(decision);
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
        verify(differenceSink).log(decision);
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
        verifyNoInteractions(differenceSink);
    }

    @ParameterizedTest
    @EnumSource(value = AuthorizationRuntimeMode.class, names = {"SHADOW", "ENFORCE"})
    void nonAvailabilityFailurePropagatesUnchanged(AuthorizationRuntimeMode mode) {
        IllegalStateException failure = new IllegalStateException("programming failure");
        when(properties.modeFor(DOMAIN)).thenReturn(mode);
        when(authorizationFacade.authorize(principal, PERMISSION)).thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(principal, DOMAIN, PERMISSION, true))
                .isSameAs(failure);
        verifyNoInteractions(differenceSink);
    }

    @ParameterizedTest(name = "rejects invalid runtime decision: {0}")
    @MethodSource("invalidRuntimeDecisions")
    void runtimeDecisionRejectsInvalidState(
            String scenario,
            UUID userId,
            String domainCode,
            String permissionCode,
            AuthorizationRuntimeMode mode,
            boolean legacyAllowed,
            AuthorizationDecision newDecision,
            boolean effectiveAllowed,
            AuthorizationComparison comparison) {
        assertThatThrownBy(() -> new AuthorizationRuntimeDecision(
                userId,
                domainCode,
                permissionCode,
                mode,
                legacyAllowed,
                newDecision,
                effectiveAllowed,
                comparison))
                .isInstanceOf(RuntimeException.class);
    }

    private static AuthorizationDecision newDecision(boolean allowed) {
        return decisionFor(PERMISSION, allowed);
    }

    private static AuthorizationDecision decisionFor(String permissionCode, boolean allowed) {
        PermissionCode permission = new PermissionCode(permissionCode);
        return allowed
                ? AuthorizationDecision.allow(permission, DOMAIN, AuthorizationScope.GROUP)
                : AuthorizationDecision.deny(
                        permission,
                        DOMAIN,
                        AuthorizationReason.PERMISSION_NOT_GRANTED);
    }

    private static Stream<Arguments> sinkFailureCases() {
        return Stream.of(
                Arguments.of(
                        "ordinary",
                        AuthorizationRuntimeMode.SHADOW,
                        new IllegalStateException("sink failure")),
                Arguments.of(
                        "authorization unavailable",
                        AuthorizationRuntimeMode.SHADOW,
                        new AuthorizationUnavailableException(
                                new IllegalStateException("sink unavailable"))),
                Arguments.of(
                        "ordinary",
                        AuthorizationRuntimeMode.ENFORCE,
                        new IllegalStateException("sink failure")),
                Arguments.of(
                        "authorization unavailable",
                        AuthorizationRuntimeMode.ENFORCE,
                        new AuthorizationUnavailableException(
                                new IllegalStateException("sink unavailable"))));
    }

    private static Stream<Arguments> invalidRuntimeDecisions() {
        AuthorizationDecision allowed = newDecision(true);
        AuthorizationDecision denied = newDecision(false);
        return Stream.of(
                invalid("null user", null, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("null domain", USER_ID, null, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("blank domain", USER_ID, " ", PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("mismatched domain", USER_ID, "unknown", PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("CRLF domain", USER_ID, "sample\r\n", PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("null permission", USER_ID, DOMAIN, null,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("blank permission", USER_ID, DOMAIN, " ",
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("CRLF permission", USER_ID, DOMAIN, "sample:approve\r\n",
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("null mode", USER_ID, DOMAIN, PERMISSION,
                        null, true, null, true, AuthorizationComparison.NOT_EVALUATED),
                invalid("null comparison", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true, null),
                invalid("legacy has new decision", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, allowed, true,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("legacy comparison evaluated", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, true,
                        AuthorizationComparison.BOTH_ALLOW),
                invalid("legacy effective differs", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.LEGACY, true, null, false,
                        AuthorizationComparison.NOT_EVALUATED),
                invalid("shadow normal misses new decision", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.SHADOW, true, null, true,
                        AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                invalid("shadow unavailable has new decision", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.SHADOW, true, denied, true,
                        AuthorizationComparison.NEW_UNAVAILABLE),
                invalid("shadow comparison inaccurate", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.SHADOW, true, denied, true,
                        AuthorizationComparison.BOTH_ALLOW),
                invalid("shadow effective differs", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.SHADOW, true, denied, false,
                        AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                invalid("shadow decision permission differs", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.SHADOW, true,
                        decisionFor("sample:read", false), true,
                        AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                invalid("enforce misses new decision", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.ENFORCE, true, null, false,
                        AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                invalid("enforce comparison inaccurate", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.ENFORCE, true, denied, false,
                        AuthorizationComparison.BOTH_ALLOW),
                invalid("enforce effective differs", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.ENFORCE, true, denied, true,
                        AuthorizationComparison.OLD_ALLOW_NEW_DENY),
                invalid("enforce unavailable comparison", USER_ID, DOMAIN, PERMISSION,
                        AuthorizationRuntimeMode.ENFORCE, true, denied, false,
                        AuthorizationComparison.NEW_UNAVAILABLE));
    }

    private static Arguments invalid(
            String scenario,
            UUID userId,
            String domainCode,
            String permissionCode,
            AuthorizationRuntimeMode mode,
            boolean legacyAllowed,
            AuthorizationDecision newDecision,
            boolean effectiveAllowed,
            AuthorizationComparison comparison) {
        return Arguments.of(
                scenario,
                userId,
                domainCode,
                permissionCode,
                mode,
                legacyAllowed,
                newDecision,
                effectiveAllowed,
                comparison);
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
