package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationComparison;
import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import com.colonel.saas.domain.user.port.AuthorizationDifferenceSink;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthorizationRuntimeService {

    private final AuthorizationFacade authorizationFacade;
    private final AuthorizationRuntimeProperties properties;
    private final AuthorizationDifferenceSink differenceSink;

    public AuthorizationRuntimeService(
            AuthorizationFacade authorizationFacade,
            AuthorizationRuntimeProperties properties,
            AuthorizationDifferenceSink differenceSink) {
        this.authorizationFacade = authorizationFacade;
        this.properties = properties;
        this.differenceSink = differenceSink;
    }

    public AuthorizationRuntimeDecision evaluate(
            AuthorizationPrincipal principal,
            String domainCode,
            String permissionCode,
            boolean legacyAllowed) {
        Objects.requireNonNull(principal, "principal must not be null");
        PermissionCode permission = new PermissionCode(permissionCode);
        String canonicalPermission = permission.value();
        String canonicalDomain = canonicalPermission.substring(
                0, canonicalPermission.indexOf(':'));
        if (!canonicalDomain.equals(domainCode)) {
            throw new IllegalArgumentException(
                    "domainCode must exactly match permission resource");
        }

        AuthorizationRuntimeMode mode = properties.modeFor(canonicalDomain);
        if (mode == null) {
            throw new IllegalStateException("authorization runtime mode must not be null");
        }
        if (mode == AuthorizationRuntimeMode.LEGACY) {
            return new AuthorizationRuntimeDecision(
                    principal.userId(),
                    canonicalDomain,
                    canonicalPermission,
                    mode,
                    legacyAllowed,
                    null,
                    legacyAllowed,
                    AuthorizationComparison.NOT_EVALUATED);
        }

        AuthorizationDecision newDecision;
        try {
            newDecision = authorizationFacade.authorize(principal, canonicalPermission);
        } catch (AuthorizationUnavailableException unavailable) {
            if (mode == AuthorizationRuntimeMode.ENFORCE) {
                throw unavailable;
            }
            AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                    principal.userId(),
                    canonicalDomain,
                    canonicalPermission,
                    mode,
                    legacyAllowed,
                    null,
                    legacyAllowed,
                    AuthorizationComparison.NEW_UNAVAILABLE);
            emitSafely(result);
            return result;
        }

        if (newDecision == null) {
            throw new IllegalStateException("authorization facade decision must not be null");
        }
        if (!canonicalPermission.equals(newDecision.permissionCode())) {
            throw new IllegalStateException(
                    "authorization facade decision permission must match request");
        }

        AuthorizationComparison comparison = compare(
                legacyAllowed, newDecision.allowed());
        boolean effectiveAllowed = mode == AuthorizationRuntimeMode.ENFORCE
                ? newDecision.allowed()
                : legacyAllowed;
        AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                principal.userId(),
                canonicalDomain,
                canonicalPermission,
                mode,
                legacyAllowed,
                newDecision,
                effectiveAllowed,
                comparison);
        emitSafely(result);
        return result;
    }

    public AuthorizationRuntimeDecision require(
            AuthorizationPrincipal principal,
            String domainCode,
            String permissionCode,
            boolean legacyAllowed) {
        AuthorizationRuntimeDecision decision = evaluate(
                principal, domainCode, permissionCode, legacyAllowed);
        if (!decision.effectiveAllowed()) {
            throw new ForbiddenException("无权限访问该接口");
        }
        return decision;
    }

    private AuthorizationComparison compare(boolean legacyAllowed, boolean newAllowed) {
        if (legacyAllowed && newAllowed) {
            return AuthorizationComparison.BOTH_ALLOW;
        }
        if (!legacyAllowed && !newAllowed) {
            return AuthorizationComparison.BOTH_DENY;
        }
        return legacyAllowed
                ? AuthorizationComparison.OLD_ALLOW_NEW_DENY
                : AuthorizationComparison.OLD_DENY_NEW_ALLOW;
    }

    private void emitSafely(AuthorizationRuntimeDecision decision) {
        try {
            differenceSink.log(decision);
        } catch (RuntimeException ignored) {
            // Difference telemetry is best effort and must not change authorization semantics.
        }
    }
}
