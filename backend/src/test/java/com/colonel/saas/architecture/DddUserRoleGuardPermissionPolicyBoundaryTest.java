package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserRoleGuardPermissionPolicyBoundaryTest {

    @Test
    void roleGuardAspect_shouldDelegateRoleMatchingToUserPermissionPolicy() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("currentRoles.contains")
                .doesNotContain("toLowerCase(Locale.ROOT)")
                .contains("CurrentUserPermissionPolicy")
                .contains("hasAnyRole")
                .contains("normalizeRoleCodes");
    }
}
