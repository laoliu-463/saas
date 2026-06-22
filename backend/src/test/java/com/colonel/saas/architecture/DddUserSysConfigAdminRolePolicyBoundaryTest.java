package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserSysConfigAdminRolePolicyBoundaryTest {

    @Test
    void sysConfigController_shouldDelegateAdminRoleMatchingToUserPermissionPolicy() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/controller/SysConfigController.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/controller/SysConfigController.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("String.valueOf(roleCodes).contains")
                .doesNotContain("roles.stream().anyMatch")
                .contains("CurrentUserPermissionPolicy")
                .contains("hasAnyRole(roleCodes, RoleCodes.ADMIN)");
    }
}
