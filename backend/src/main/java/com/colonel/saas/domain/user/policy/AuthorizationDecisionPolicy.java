package com.colonel.saas.domain.user.policy;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationScope;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationSnapshot;
import com.colonel.saas.domain.user.domain.GrantedRolePermission;

import java.util.Comparator;
import java.util.List;

public class AuthorizationDecisionPolicy {

    public AuthorizationDecision decide(
            PermissionCode permission,
            AuthorizationSnapshot snapshot) {
        if (snapshot == null) {
            return AuthorizationDecision.deny(
                    permission,
                    null,
                    AuthorizationReason.DOMAIN_SCOPE_MISSING);
        }

        List<GrantedRolePermission> matchingGrants = snapshot.grants().stream()
                .filter(grant -> permission.equals(grant.permission()))
                .toList();

        if (matchingGrants.isEmpty()) {
            return AuthorizationDecision.deny(
                    permission,
                    null,
                    AuthorizationReason.PERMISSION_NOT_GRANTED);
        }

        String domainCode = matchingGrants.get(0).domainCode();
        String denialDomainCode = matchingGrants.stream()
                .map(GrantedRolePermission::domainCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(null);
        boolean dataScopeRequired = matchingGrants.get(0).dataScopeRequired();
        boolean domainMetadataValid = domainCode != null
                && !domainCode.isBlank()
                && matchingGrants.stream()
                        .allMatch(grant -> domainCode.equals(grant.domainCode()));
        boolean dataScopeRequirementConsistent = matchingGrants.stream()
                .allMatch(grant -> grant.dataScopeRequired() == dataScopeRequired);
        boolean scopedMetadataValid = !dataScopeRequired
                || matchingGrants.stream().allMatch(grant -> grant.scope() != null);

        if (!domainMetadataValid
                || !dataScopeRequirementConsistent
                || !scopedMetadataValid) {
            return AuthorizationDecision.deny(
                    permission,
                    denialDomainCode,
                    AuthorizationReason.DOMAIN_SCOPE_MISSING);
        }

        if (!dataScopeRequired) {
            return AuthorizationDecision.allow(
                    permission,
                    domainCode,
                    AuthorizationScope.ALL);
        }

        if (snapshot.subject().deptId() == null) {
            return AuthorizationDecision.deny(
                    permission,
                    domainCode,
                    AuthorizationReason.DOMAIN_SCOPE_MISSING);
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
