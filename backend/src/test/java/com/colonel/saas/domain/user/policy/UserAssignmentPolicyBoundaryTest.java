package com.colonel.saas.domain.user.policy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserAssignmentPolicyBoundaryTest {

    @Test
    void policy_shouldDependOnUserDomainPortInsteadOfPersistenceTypes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicy.java"));

        assertThat(source).contains("UserAssignmentLookup");
        assertThat(source)
                .doesNotContain("com.colonel.saas.mapper.")
                .doesNotContain("com.colonel.saas.entity.");
    }
}
