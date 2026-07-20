package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserRoleGuardPermissionPolicyBoundaryTest {

    @Test
    void permissionGuardAspect_shouldDelegateToAuthorizationFacade() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/aspect/PermissionGuardAspect.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/aspect/PermissionGuardAspect.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("AuthorizationFacade")
                .contains("CurrentUserProvider")
                .contains("authorizationFacade.authorize")
                .contains("RequirePermission")
                .doesNotContain("RoleCodes")
                .doesNotContain("hasAnyRole");
    }

    @Test
    void permissionChecker_shouldKeepCurrentUserPermissionPolicyAsRuleSource() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionChecker.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionChecker.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole")
                .contains("currentUserPermissionPolicy.normalizeRoleCodes")
                .contains("currentUserPermissionPolicy.checkPermission")
                .doesNotContain("roleCodes.toString()")
                .doesNotContain("instanceof Collection");
    }

    @Test
    void userDomainService_shouldUsePermissionCheckerForCurrentUserPermissions() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/service/UserDomainService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/service/UserDomainService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.resolveRoleCodes")
                .contains("currentUserPermissionChecker.resolveDataScopeCode")
                .contains("currentUserPermissionChecker.checkPermission")
                .doesNotContain("private final CurrentUserPermissionPolicy");
    }

    @Test
    void legacyUserDomainFacade_shouldUsePermissionCheckerForRoleHelpers() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole")
                .contains("currentUserPermissionChecker.normalizeRoleCodes")
                .doesNotContain("private final CurrentUserPermissionPolicy");
    }

    @Test
    void domainPolicyConfig_shouldExposePermissionCheckerBean() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/config/DomainPolicyConfig.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker(")
                .contains("new CurrentUserPermissionChecker(currentUserPermissionPolicy)")
                .contains("sampleActionPermissionPolicy(")
                .contains("CurrentUserPermissionChecker currentUserPermissionChecker")
                .contains("new SampleActionPermissionPolicy(currentUserPermissionChecker)");
    }
}
