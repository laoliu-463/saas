package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User-domain checker for current-user role and operation permissions.
 *
 * <p>The checker keeps {@link CurrentUserPermissionPolicy} as the pure rule
 * source while giving application and adapter edges one stable dependency for
 * permission checks.</p>
 */
public class CurrentUserPermissionChecker {

    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    public CurrentUserPermissionChecker(CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.currentUserPermissionPolicy = Objects.requireNonNull(
                currentUserPermissionPolicy,
                "currentUserPermissionPolicy");
    }

    public List<String> resolveRoleCodes(List<RolePermission> roles, List<String> requestRoleCodes) {
        return currentUserPermissionPolicy.resolveRoleCodes(roles, requestRoleCodes);
    }

    public int resolveDataScopeCode(List<RolePermission> roles, DataScope requestScope, List<String> roleCodes) {
        return currentUserPermissionPolicy.resolveDataScopeCode(roles, requestScope, roleCodes);
    }

    public Map<String, Object> mergePermissions(List<RolePermission> roles, int dataScopeCode) {
        return currentUserPermissionPolicy.mergePermissions(roles, dataScopeCode);
    }

    public CheckPermissionResponse checkPermission(
            List<String> requestRoleCodes,
            List<RolePermission> roles,
            CheckPermissionRequest request) {
        return currentUserPermissionPolicy.checkPermission(requestRoleCodes, roles, request);
    }

    public boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, expectedRoles);
    }

    public List<String> normalizeRoleCodes(Object roleCodes) {
        return currentUserPermissionPolicy.normalizeRoleCodes(roleCodes);
    }

    public String scopeName(int code) {
        return currentUserPermissionPolicy.scopeName(code);
    }
}
