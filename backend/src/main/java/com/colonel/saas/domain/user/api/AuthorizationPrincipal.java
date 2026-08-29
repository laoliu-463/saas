package com.colonel.saas.domain.user.api;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationPrincipal(
        UUID userId,
        UUID deptId,
        String username,
        long authzVersion,
        boolean pendingActivation) {

    public AuthorizationPrincipal {
        Objects.requireNonNull(userId, "userId");
        if (authzVersion < 1) {
            throw new IllegalArgumentException("authzVersion must be positive");
        }
    }
}
