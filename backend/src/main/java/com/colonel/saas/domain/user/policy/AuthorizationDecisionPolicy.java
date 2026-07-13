package com.colonel.saas.domain.user.policy;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;

import java.util.Comparator;
import java.util.List;

public class AuthorizationDecisionPolicy {

    public AuthorizationDecision decide(
            PermissionCode permission,
            List<GrantedRolePermission> grants) {
        List<GrantedRolePermission> matchingGrants = grants == null
                ? List.of()
                : grants.stream()
                        .filter(grant -> permission.equals(grant.permission()))
                        .toList();

        if (matchingGrants.isEmpty()) {
            return AuthorizationDecision.deny(
                    permission,
                    null,
                    AuthorizationReason.PERMISSION_NOT_GRANTED);
        }

        String domainCode = matchingGrants.get(0).domainCode();
        if (matchingGrants.stream().anyMatch(grant -> !grant.dataScopeRequired())) {
            return AuthorizationDecision.allow(
                    permission,
                    domainCode,
                    AuthorizationScope.ALL);
        }

        AuthorizationScope widestScope = matchingGrants.stream()
                .map(GrantedRolePermission::scope)
                .max(Comparator.comparingInt(AuthorizationScope::ordinal))
                .orElse(AuthorizationScope.DENY);

        if (widestScope == AuthorizationScope.DENY) {
            return AuthorizationDecision.deny(
                    permission,
                    domainCode,
                    AuthorizationReason.DOMAIN_SCOPE_MISSING);
        }

        return AuthorizationDecision.allow(permission, domainCode, widestScope);
    }
}
