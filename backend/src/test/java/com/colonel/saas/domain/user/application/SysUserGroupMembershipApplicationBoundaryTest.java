package com.colonel.saas.domain.user.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserGroupMembershipApplicationBoundaryTest {

    @Test
    void applicationService_shouldUsePortInsteadOfPersistenceTypes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java"));

        assertThat(source).contains("UserGroupMembershipStore");
        assertThat(source)
                .doesNotContain("com.colonel.saas.mapper.")
                .doesNotContain("com.colonel.saas.entity.");
    }
}
