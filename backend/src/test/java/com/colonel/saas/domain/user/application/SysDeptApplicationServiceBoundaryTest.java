package com.colonel.saas.domain.user.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysDeptApplicationServiceBoundaryTest {

    @Test
    void applicationServiceShouldUseLeaderDisplayLookupInsteadOfUserPersistence() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/SysDeptApplicationService.java"));

        assertThat(source).contains("OrgLeaderDisplayLookup");
        assertThat(source).contains("OrgDepartmentRepository");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.SysUserMapper");
        assertThat(source).doesNotContain("com.colonel.saas.entity.SysUser");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.SysDeptMapper");
        assertThat(source).doesNotContain("com.colonel.saas.entity.SysDept");
    }
}
