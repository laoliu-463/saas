package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeExclusiveMerchantApplicationBoundaryTest {

    @Test
    void exclusiveMerchantApplication_shouldUseOwnershipReferenceInsteadOfFullUserDto() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/performance/application/ExclusiveMerchantApplicationService.java"));

        assertThat(source)
                .doesNotContain("com.colonel.saas.dto.user.UserOptionResponse")
                .doesNotContain("List<UserOptionResponse>")
                .doesNotContain("userDomainFacade.getUsersByIds")
                .contains("userDomainFacade.loadUserOwnershipReferencesByIds");
    }
}
