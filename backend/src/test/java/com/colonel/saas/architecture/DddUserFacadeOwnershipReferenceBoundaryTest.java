package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeOwnershipReferenceBoundaryTest {

    @Test
    void talentAndMerchantAssignment_shouldUseOwnershipReferenceInsteadOfFullUserDto() throws Exception {
        String talentService = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentService.java"));
        String merchantService = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/MerchantService.java"));

        assertThat(talentService)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse targetUser = userDomainFacade.getUserById")
                .contains("userDomainFacade.loadUserOwnershipReferencesByIds");
        assertThat(merchantService)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse targetUser = userDomainFacade.getUserById")
                .contains("userDomainFacade.loadUserOwnershipReferencesByIds");
    }

    @Test
    void talentRelease_shouldDelegateRoleMatchingToUserPermissionChecker() throws Exception {
        String talentService = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentService.java"));
        String talentClaimApplicationService = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java"));

        assertThat(talentService)
                .doesNotContain("private boolean hasRole")
                .doesNotContain("String target = role")
                .doesNotContain("roleCodes.stream()")
                .contains("talentClaimApplicationService.release");
        assertThat(talentClaimApplicationService)
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("private final CurrentUserPermissionPolicy")
                .doesNotContain("String target = role")
                .doesNotContain("roleCodes.stream()")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole");
    }
}
