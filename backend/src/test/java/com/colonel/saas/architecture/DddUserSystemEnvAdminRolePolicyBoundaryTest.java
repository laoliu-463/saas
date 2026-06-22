package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserSystemEnvAdminRolePolicyBoundaryTest {

    @Test
    void systemEnvController_shouldDelegateAdminRoleMatchingToUserPermissionPolicy() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/controller/SystemEnvController.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/controller/SystemEnvController.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("currentRoles().stream().anyMatch")
                .doesNotContain("private List<String> currentRoles")
                .doesNotContain("Objects.toString(raw")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole(currentRoleCodes(), RoleCodes.ADMIN)");
    }
}
