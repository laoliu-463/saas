package com.colonel.saas.domain.user.api;

import java.util.Objects;

public record AuthorizationDecision(
        boolean allowed,
        String permissionCode,
        String domainCode,
        AuthorizationScope scope,
        AuthorizationReason reason) {

    public AuthorizationDecision {
        Objects.requireNonNull(permissionCode, "permissionCode");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(reason, "reason");

        if (allowed && scope == AuthorizationScope.DENY) {
            throw new IllegalArgumentException("allowed decision must not use DENY scope");
        }
        if (allowed && reason != AuthorizationReason.GRANTED) {
            throw new IllegalArgumentException("allowed decision must use GRANTED reason");
        }
        if (!allowed && scope != AuthorizationScope.DENY) {
            throw new IllegalArgumentException("denied decision must use DENY scope");
        }
        if (!allowed && reason == AuthorizationReason.GRANTED) {
            throw new IllegalArgumentException("denied decision must not use GRANTED reason");
        }
    }

    public static AuthorizationDecision allow(
            PermissionCode permission,
            String domain,
            AuthorizationScope scope) {
        return new AuthorizationDecision(
                true,
                permission.value(),
                domain,
                scope,
                AuthorizationReason.GRANTED);
    }

    public static AuthorizationDecision deny(
            PermissionCode permission,
            String domain,
            AuthorizationReason reason) {
        return new AuthorizationDecision(
                false,
                permission.value(),
                domain,
                AuthorizationScope.DENY,
                reason);
    }
}
