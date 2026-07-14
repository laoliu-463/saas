package com.colonel.saas.domain.user.domain;

import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;

import java.util.UUID;

public record GrantedRolePermission(
        UUID roleId,
        PermissionCode permission,
        String domainCode,
        boolean dataScopeRequired,
        AuthorizationScope scope) {
}
