package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserPermissionPolicySamplePortBoundaryTest {

    @Test
    void sampleApplicationPort_shouldDelegateRoleCodeMatchingToUserPermissionChecker() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java"));

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("private final CurrentUserPermissionPolicy")
                .doesNotContain("currentUserPermissionPolicy.hasAnyRole")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole");
    }

    @Test
    void sampleActionPermissionPolicy_shouldDelegateRoleCodeMatchingToUserPermissionChecker() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java"));

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("private final CurrentUserPermissionPolicy")
                .doesNotContain("currentUserPermissionPolicy.hasAnyRole")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole");
    }

    @Test
    void sampleApplicationService_shouldDelegateRoleCodeMatchingToUserPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java"));

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .doesNotContain("roleCodes.toString()")
                .doesNotContain("roleCodes instanceof Collection")
                .doesNotContain("CurrentUserPermissionPolicy")
                .doesNotContain("currentUserPermissionPolicy.hasAnyRole")
                .contains("SampleActionPermissionPolicy")
                .contains("sampleActionPermissionPolicy");
    }

    @Test
    void sampleLogisticsImport_shouldDelegateActionPermissionToSamplePolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/SampleLogisticsImportService.java"));

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .doesNotContain("roleCodes.toString()")
                .doesNotContain("roleCodes instanceof Collection")
                .doesNotContain("CurrentUserPermissionPolicy")
                .contains("SampleActionPermissionPolicy")
                .contains("sampleActionPermissionPolicy");
    }
}
