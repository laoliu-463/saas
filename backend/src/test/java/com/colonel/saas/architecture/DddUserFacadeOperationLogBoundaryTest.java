package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeOperationLogBoundaryTest {

    @Test
    void operationLogService_shouldConsumeUserNameInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/OperationLogService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse user = userDomainFacade.getUserById");
        assertThat(source)
                .contains("userDomainFacade.getUsername(operatorId)");
    }
}
