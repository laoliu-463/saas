package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserPermissionPolicyTest {

    private CurrentUserPermissionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CurrentUserPermissionPolicy();
    }

    @Test
    void resolveRoleCodes_shouldPreferDbRolesAndPreserveOrder() {
        List<String> roleCodes = policy.resolveRoleCodes(
                List.of(
                        role(RoleCodes.CHANNEL_STAFF, 1, Map.of()),
                        role(RoleCodes.CHANNEL_LEADER, 2, Map.of()),
                        role(RoleCodes.CHANNEL_STAFF, 1, Map.of())
                ),
                List.of(RoleCodes.ADMIN)
        );

        assertThat(roleCodes).containsExactly(RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER);
    }

    @Test
    void resolveDataScopeCode_shouldForceAdminAndOpsToAll() {
        assertThat(policy.resolveDataScopeCode(
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of())),
                DataScope.PERSONAL,
                List.of(RoleCodes.ADMIN)
        )).isEqualTo(DataScope.ALL.getCode());

        assertThat(policy.resolveDataScopeCode(
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of())),
                DataScope.PERSONAL,
                List.of(RoleCodes.OPS_STAFF)
        )).isEqualTo(DataScope.ALL.getCode());
    }

    @Test
    void mergePermissions_shouldAggregateMenusOperationsAndScopeName() {
        Map<String, Object> permissions = policy.mergePermissions(List.of(
                role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "menus", List.of("product", "talent"),
                        "operations", Map.of("product", List.of("audit"))
                )),
                role(RoleCodes.BIZ_LEADER, 2, Map.of(
                        "menus", List.of("product", "dashboard"),
                        "operations", Map.of("product", List.of("assign"), "sample", "*")
                ))
        ), DataScope.DEPT.getCode());

        assertThat(permissions.get("menus")).isEqualTo(List.of("product", "talent", "dashboard"));
        assertThat(((Map<?, ?>) permissions.get("operations")).get("product"))
                .isEqualTo(List.of("audit", "assign"));
        assertThat(((Map<?, ?>) permissions.get("operations")).get("sample"))
                .isEqualTo(List.of("*"));
        assertThat(permissions.get("data_scope")).isEqualTo("group");
    }

    @Test
    void checkPermission_shouldAllowAdminAndConfiguredOperations() {
        assertThat(policy.checkPermission(
                List.of(RoleCodes.ADMIN),
                List.of(),
                new CheckPermissionRequest("Product", "Delete")
        ).allowed()).isTrue();

        var allowed = policy.checkPermission(
                List.of(RoleCodes.BIZ_STAFF),
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "operations", Map.of("product", List.of("audit"))
                ))),
                new CheckPermissionRequest(" Product ", " Audit ")
        );
        var denied = policy.checkPermission(
                List.of(RoleCodes.BIZ_STAFF),
                List.of(role(RoleCodes.BIZ_STAFF, 1, Map.of(
                        "operations", Map.of("product", List.of("audit"))
                ))),
                new CheckPermissionRequest("product", "export")
        );

        assertThat(allowed.resource()).isEqualTo("product");
        assertThat(allowed.action()).isEqualTo("audit");
        assertThat(allowed.allowed()).isTrue();
        assertThat(denied.allowed()).isFalse();
    }

    @Test
    void source_shouldNotDependOnPersistenceEntity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionPolicy.java"));

        assertThat(source).doesNotContain("entity.SysRole");
    }

    private RolePermission role(String roleCode, Integer dataScope, Map<String, Object> permissions) {
        return new RolePermission(roleCode, dataScope, permissions);
    }
}
