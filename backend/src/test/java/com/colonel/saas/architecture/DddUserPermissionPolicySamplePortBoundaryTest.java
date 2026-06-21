package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserPermissionPolicySamplePortBoundaryTest {

    @Test
    void sampleApplicationPort_shouldDelegateRoleCodeMatchingToUserPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java"));

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole");
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
}
