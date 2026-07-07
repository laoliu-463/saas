package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void talentQuery_shouldDelegateRoleCodeMatchingToUserPermissionChecker() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentQueryService.java"));

        assertThat(source)
                .doesNotContain("private boolean hasRole")
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("private final CurrentUserPermissionPolicy")
                .doesNotContain("currentUserPermissionPolicy.hasAnyRole")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole");
    }

    @Test
    void talentPermissionConsumers_shouldUseUserCheckerAndDataScopeResolver() throws Exception {
        List<String> files = List.of(
                "src/main/java/com/colonel/saas/service/TalentService.java",
                "src/main/java/com/colonel/saas/service/TalentQueryService.java",
                "src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java",
                "src/main/java/com/colonel/saas/domain/talent/application/TalentPageApplicationService.java",
                "src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentCheckApplicationService.java"
        );

        for (String file : files) {
            String source = Files.readString(Path.of(file));
            assertThat(source)
                    .describedAs(file)
                    .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                    .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                    .doesNotContain("private final CurrentUserPermissionPolicy")
                    .doesNotContain("private final DataScopePolicy")
                    .doesNotContain("currentUserPermissionPolicy.")
                    .doesNotContain("dataScopePolicy.")
                    .doesNotContain("DataScopePolicy.Decision")
                    .doesNotContain("DataScopePolicy.ContextRequirement");
        }
        assertThat(Files.readString(Path.of("src/main/java/com/colonel/saas/service/TalentQueryService.java")))
                .contains("CurrentUserPermissionChecker")
                .contains("DataScopeResolver");
    }
}
