package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeProductServiceBoundaryTest {

    @Test
    void productService_shouldConsumeUserScalarsInsteadOfUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/ProductService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("UserOptionResponse")
                .doesNotContain("userDomainFacade.getUserById")
                .doesNotContain("userDomainFacade.getUsersByIds");
        assertThat(source)
                .contains("userDomainFacade.loadUserDisplayLabelsByIds")
                .contains("userDomainFacade.loadUserOwnershipReferencesByIds")
                .contains("userDomainFacade.loadUserChannelCodesByIds");
    }
}
