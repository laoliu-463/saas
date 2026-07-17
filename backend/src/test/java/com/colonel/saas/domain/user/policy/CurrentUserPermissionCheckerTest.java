package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserPermissionCheckerTest {

    private final CurrentUserPermissionPolicy policy = new CurrentUserPermissionPolicy();
    private final CurrentUserPermissionChecker checker = new CurrentUserPermissionChecker(policy);

    @Test
    void hasAnyRole_shouldMatchPolicyNormalizationBehavior() {
        Object roleCodes = "[ ADMIN , biz_leader , ADMIN ]";

        assertThat(checker.hasAnyRole(roleCodes, "admin"))
                .isEqualTo(policy.hasAnyRole(roleCodes, "admin"));
        assertThat(checker.normalizeRoleCodes(roleCodes))
                .containsExactlyElementsOf(policy.normalizeRoleCodes(roleCodes));
    }

    @Test
    void hasOnlyCanonicalRole_shouldDelegateCompositeRoleSemantics() {
        Object roleCodes = List.of(RoleCodes.OPS_STAFF, RoleCodes.BIZ_STAFF);

        assertThat(checker.hasOnlyCanonicalRole(roleCodes, RoleCodes.OPS_STAFF))
                .isEqualTo(policy.hasOnlyCanonicalRole(roleCodes, RoleCodes.OPS_STAFF))
                .isFalse();
    }

    @Test
    void resolveDataScopeCode_shouldDelegateWithoutChangingPolicyBehavior() {
        List<RolePermission> roles = List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of()));
        List<String> roleCodes = List.of(RoleCodes.OPS_STAFF);

        assertThat(checker.resolveDataScopeCode(roles, DataScope.PERSONAL, roleCodes))
                .isEqualTo(policy.resolveDataScopeCode(roles, DataScope.PERSONAL, roleCodes));
    }

    @Test
    void checkPermission_shouldKeepAdminAndConfiguredOperationBehavior() {
        var admin = checker.checkPermission(
                List.of(RoleCodes.ADMIN),
                List.of(),
                new CheckPermissionRequest("Product", "Delete"));
        var configured = checker.checkPermission(
                List.of(RoleCodes.BIZ_STAFF),
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "operations", Map.of("product", List.of("audit"))
                ))),
                new CheckPermissionRequest(" Product ", " Audit "));

        assertThat(admin.allowed()).isTrue();
        assertThat(configured.resource()).isEqualTo("product");
        assertThat(configured.action()).isEqualTo("audit");
        assertThat(configured.allowed()).isTrue();
    }

    @Test
    void checkPermission_shouldKeepDeniedAndWildcardBehavior() {
        var wildcard = checker.checkPermission(
                List.of(RoleCodes.BIZ_STAFF),
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "operations", Map.of("product", List.of("*"))
                ))),
                new CheckPermissionRequest("Product", "Archive"));
        var denied = checker.checkPermission(
                List.of(RoleCodes.BIZ_STAFF),
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "operations", Map.of("product", List.of("audit"))
                ))),
                new CheckPermissionRequest("Product", "Archive"));

        assertThat(wildcard.allowed()).isTrue();
        assertThat(denied.allowed()).isFalse();
    }

    @Test
    void mergePermissions_shouldDelegateWithoutChangingOutput() {
        List<RolePermission> roles = List.of(
                role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "menus", List.of("product"),
                        "operations", Map.of("product", List.of("audit"))
                )),
                role(RoleCodes.BIZ_LEADER, 2, Map.of(
                        "menus", List.of("dashboard"),
                        "operations", Map.of("product", List.of("assign"))
                )));

        assertThat(checker.mergePermissions(roles, DataScope.DEPT.getCode()))
                .isEqualTo(policy.mergePermissions(roles, DataScope.DEPT.getCode()));
    }

    @Test
    void resolveRoleCodesAndScopeName_shouldDelegateWithoutChangingPolicyBehavior() {
        List<RolePermission> roles = List.of(role(RoleCodes.CHANNEL_LEADER, DataScope.DEPT.getCode(), Map.of()));

        assertThat(checker.resolveRoleCodes(roles, List.of(RoleCodes.ADMIN)))
                .containsExactlyElementsOf(policy.resolveRoleCodes(roles, List.of(RoleCodes.ADMIN)));
        assertThat(checker.scopeName(DataScope.ALL.getCode()))
                .isEqualTo(policy.scopeName(DataScope.ALL.getCode()));
    }

    private RolePermission role(String roleCode, Integer dataScope, Map<String, Object> permissions) {
        return new RolePermission(roleCode, dataScope, permissions);
    }
}
