package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserMasterDataPermissionPolicyBoundaryTest {

    @Test
    void userMasterData_shouldDelegateRoleCodeNormalizationToUserPermissionPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/UserMasterDataService.java"));

        assertThat(source)
                .doesNotContain("roleCodes.stream()")
                .doesNotContain("roleCodes.contains(RoleCodes.ADMIN)")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy");
    }
}
