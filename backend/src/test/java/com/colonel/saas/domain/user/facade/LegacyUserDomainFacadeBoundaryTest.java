package com.colonel.saas.domain.user.facade;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyUserDomainFacadeBoundaryTest {

    @Test
    void facadeShouldUseUserBasicLookupPortForUserReadModels() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java"));

        assertThat(source).contains("UserBasicLookup");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.SysUserMapper");
        assertThat(source).doesNotContain("com.colonel.saas.entity.SysUser");
    }

    @Test
    void facadeShouldUseDepartmentOptionLookupPortForDepartmentOptions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java"));

        assertThat(source).contains("DepartmentOptionLookup");
        assertThat(source).doesNotContain("com.colonel.saas.service.SysDeptService");
        assertThat(source).doesNotContain("com.colonel.saas.entity.SysDept");
    }
}
