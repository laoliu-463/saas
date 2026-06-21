package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeTalentQueryBoundaryTest {

    @Test
    void talentQuery_shouldConsumeUserDisplayLabelsInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentQueryService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("userDomainFacade.getUsersByIds")
                .doesNotContain("UserOptionResponse owner");
        assertThat(source)
                .contains("userDomainFacade.loadUserDisplayLabelsByIds");
    }

    @Test
    void talentQuery_shouldDelegateRoleCodeMatchingToUserPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentQueryService.java"));

        assertThat(source)
                .doesNotContain("private boolean hasRole")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole");
    }
}
