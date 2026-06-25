package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeDataApplicationBoundaryTest {

    @Test
    void dataApplication_shouldConsumeUserDisplayNamesInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/data/DataApplicationService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("userDomainFacade.getUsersByIds")
                .doesNotContain("List<UserOptionResponse>")
                .doesNotContain("for (UserOptionResponse");
        assertThat(source)
                .contains("userDomainFacade.loadUserDisplayNamesByIds");
    }
}
