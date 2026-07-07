package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserRoleGuardPermissionPolicyBoundaryTest {

    @Test
    void roleGuardAspect_shouldDelegateRoleMatchingToUserPermissionChecker() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("currentRoles.contains")
                .doesNotContain("toLowerCase(Locale.ROOT)")
                .contains("CurrentUserPermissionChecker")
                .contains("hasAnyRole")
                .contains("normalizeRoleCodes");
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
