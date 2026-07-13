package com.colonel.saas.domain.user.api;

public record AuthorizationDecision(
        boolean allowed,
        String permissionCode,
        String domainCode,
        AuthorizationScope scope,
        AuthorizationReason reason) {

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
