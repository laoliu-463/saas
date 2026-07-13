package com.colonel.saas.domain.user.domain;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationSubject(UUID userId, UUID deptId, long authzVersion) {

    public AuthorizationSubject {
        Objects.requireNonNull(userId, "userId");
        if (authzVersion < 1) {
            throw new IllegalArgumentException("authzVersion must be positive");
        }
    }
}
