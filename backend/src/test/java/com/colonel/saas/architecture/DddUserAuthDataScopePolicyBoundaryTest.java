package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserAuthDataScopePolicyBoundaryTest {

    @Test
    void authApplication_shouldDelegateDataScopeRoleMatchingToUserPermissionPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/AuthApplication.java"));

        assertThat(source)
                .doesNotContain("roleCodes.contains(RoleCodes.")
                .contains("CurrentUserPermissionPolicy")
                .contains("resolveDataScopeCode");
    }

    @Test
    void authService_shouldStayLegacyDelegateShell() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/AuthService.java"));

        assertThat(source)
                .doesNotContain("roleCodes.contains(RoleCodes.")
                .contains("AuthApplication")
                .contains("authApplication.login(request)")
                .contains("authApplication.refreshToken(request)");
    }
}
