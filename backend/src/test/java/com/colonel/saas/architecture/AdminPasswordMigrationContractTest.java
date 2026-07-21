package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 迁移脚本不得把种子密码传播到已有管理员账号。 */
class AdminPasswordMigrationContractTest {

    @Test
    void migrateAllShouldNotReadOrRequireAdminPassword() throws IOException {
        String source = Files.readString(projectPath(
                "src/main/resources/db/migrate-all.sql"));

        assertThat(source)
                .doesNotContain("\\getenv admin_password ADMIN_PASSWORD")
                .doesNotContain("ADMIN_PASSWORD is required for password migration")
                .contains("ADMIN_PASSWORD 仅用于 PostgreSQL volume 首次初始化时的 init-db.sql");
    }

    private static Path projectPath(String relativePath) {
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }
}
