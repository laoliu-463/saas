package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeSampleApplicationBoundaryTest {

    @Test
    void sampleApplication_shouldConsumeUserScalarsInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse user = userDomainFacade.getUserById")
                .doesNotContain("userDomainFacade.getUserById")
                .doesNotContain("userDomainFacade.getUsersByIds");
        assertThat(source)
                .contains("userDomainFacade.loadUserOwnershipReferencesByIds")
                .contains("userDomainFacade.loadUserDisplayLabelsByIds");
    }
}
