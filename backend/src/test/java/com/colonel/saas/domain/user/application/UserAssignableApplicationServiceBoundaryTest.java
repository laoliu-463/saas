package com.colonel.saas.domain.user.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserAssignableApplicationServiceBoundaryTest {

    @Test
    void applicationService_shouldUsePortsInsteadOfPersistenceTypes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/UserAssignableApplicationService.java"));

        assertThat(source).contains("UserAssignableCandidateLookup");
        assertThat(source)
                .doesNotContain("com.baomidou.mybatisplus")
                .doesNotContain("com.colonel.saas.mapper.")
                .doesNotContain("com.colonel.saas.entity.");
    }
}
