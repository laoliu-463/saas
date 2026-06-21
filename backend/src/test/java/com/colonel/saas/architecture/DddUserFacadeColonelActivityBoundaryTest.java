package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeColonelActivityBoundaryTest {

    @Test
    void colonelActivityController_shouldConsumeUserDisplayNamesInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/controller/ColonelActivityController.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse user = userDomainFacade.getUserById")
                .doesNotContain("user.realName()")
                .doesNotContain("user.username()");
        assertThat(source)
                .contains("userDomainFacade.loadUserDisplayNamesByIds");
    }
}
