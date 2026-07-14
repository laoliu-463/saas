package com.colonel.saas.domain.user.domain;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;

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
}
