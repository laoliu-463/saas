package com.colonel.saas.domain.user.domain;

import java.util.List;
import java.util.Objects;

public record AuthorizationSnapshot(
        AuthorizationSubject subject,
        List<GrantedRolePermission> grants) {

    public AuthorizationSnapshot {
        Objects.requireNonNull(subject, "subject");
        grants = grants == null ? List.of() : List.copyOf(grants);
    }
}
