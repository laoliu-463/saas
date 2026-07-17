package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeSampleFilterBoundaryTest {

    @Test
    void sampleFilterOptions_shouldConsumeUserDisplayLabelsInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse user = userDomainFacade.getUserById")
                .doesNotContain("userDomainFacade.getUsersByIds");
        assertThat(source)
                .contains("userDomainFacade.loadUserDisplayLabelsByIds");
    }

    @Test
    void sampleFilterOptions_shouldDelegateRoleCodeMatchingToUserPermissionChecker() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java"));

        assertThat(source)
                .doesNotContain("private static boolean hasAnyRole")
                .doesNotContain("roleCodes instanceof Collection")
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("currentUserPermissionPolicy.hasAnyRole")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasOnlyCanonicalRole");
    }
}
