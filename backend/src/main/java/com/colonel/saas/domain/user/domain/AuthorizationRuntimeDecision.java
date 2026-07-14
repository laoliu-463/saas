package com.colonel.saas.domain.user.domain;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import com.colonel.saas.domain.user.api.PermissionCode;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationRuntimeDecision(
        UUID userId,
        String domainCode,
        String permissionCode,
        AuthorizationRuntimeMode mode,
        boolean legacyAllowed,
        AuthorizationDecision newDecision,
        boolean effectiveAllowed,
        AuthorizationComparison comparison) {

    public AuthorizationRuntimeDecision {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(domainCode, "domainCode must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(comparison, "comparison must not be null");

        PermissionCode permission = new PermissionCode(permissionCode);
        String canonicalDomain = permission.value().substring(
                0, permission.value().indexOf(':'));
        if (!canonicalDomain.equals(domainCode)) {
            throw new IllegalArgumentException(
                    "domainCode must exactly match permission resource");
        }
        if (newDecision != null
                && !permission.value().equals(newDecision.permissionCode())) {
            throw new IllegalArgumentException(
                    "newDecision permission must match runtime permission");
        }

        switch (mode) {
            case LEGACY -> validateLegacy(
                    legacyAllowed, newDecision, effectiveAllowed, comparison);
            case SHADOW -> validateShadow(
                    legacyAllowed, newDecision, effectiveAllowed, comparison);
            case ENFORCE -> validateEnforce(
                    legacyAllowed, newDecision, effectiveAllowed, comparison);
        }
    }

    private static void validateLegacy(
            boolean legacyAllowed,
            AuthorizationDecision newDecision,
            boolean effectiveAllowed,
            AuthorizationComparison comparison) {
        if (newDecision != null
                || comparison != AuthorizationComparison.NOT_EVALUATED
                || effectiveAllowed != legacyAllowed) {
            throw new IllegalArgumentException("invalid LEGACY runtime decision");
        }
    }

    private static void validateShadow(
            boolean legacyAllowed,
            AuthorizationDecision newDecision,
            boolean effectiveAllowed,
            AuthorizationComparison comparison) {
        if (effectiveAllowed != legacyAllowed) {
            throw new IllegalArgumentException("SHADOW effective result must equal legacy result");
        }
        if (comparison == AuthorizationComparison.NEW_UNAVAILABLE) {
            if (newDecision != null) {
                throw new IllegalArgumentException(
                        "SHADOW unavailable decision must not include new decision");
            }
            return;
        }
        if (newDecision == null || comparison != compare(legacyAllowed, newDecision.allowed())) {
            throw new IllegalArgumentException("invalid SHADOW runtime decision");
        }
    }

    private static void validateEnforce(
            boolean legacyAllowed,
            AuthorizationDecision newDecision,
            boolean effectiveAllowed,
            AuthorizationComparison comparison) {
        if (newDecision == null
                || comparison != compare(legacyAllowed, newDecision.allowed())
                || effectiveAllowed != newDecision.allowed()) {
            throw new IllegalArgumentException("invalid ENFORCE runtime decision");
        }
    }

    private static AuthorizationComparison compare(boolean legacyAllowed, boolean newAllowed) {
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
}
