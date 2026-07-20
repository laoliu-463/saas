package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPermissionCatalogMigrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void permissionCatalog_shouldSeedBuiltInRolesIdempotentlyWithoutTouchingCustomRole() {
        Map<String, Integer> expected = Map.of(
                "admin", 129,
                "biz_leader", 57,
                "biz_staff", 49,
                "channel_leader", 41,
                "channel_staff", 28,
                "ops_staff", 16,
                "custom_role", 0);
        expected.keySet().forEach(this::insertRole);

        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
                new ClassPathResource("db/alter-authorization-permission-catalog-20260720.sql"));
        migration.execute(dataSource);
        migration.execute(dataSource);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class))
                .isEqualTo(129);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role_permission", Integer.class))
                .isEqualTo(320);
        expected.forEach((roleCode, count) -> assertThat(permissionCount(roleCode))
                .as(roleCode)
                .isEqualTo(count));
        assertThat(hasPermission("ops_staff", "sample:refresh-logistics")).isTrue();
        assertThat(hasPermission("biz_staff", "talent:access")).isTrue();
        assertThat(hasPermission("channel_staff", "product:page")).isTrue();
        assertThat(hasPermission("biz_staff", "commission-rule:access")).isFalse();

        jdbcTemplate.update("""
                DELETE FROM sys_role_permission
                WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'admin')
                  AND permission_id = (
                      SELECT id FROM sys_permission
                      WHERE permission_code = 'admin-colonel-partner:sync'
                  )
                """);
        migration.execute(dataSource);

        assertThat(permissionCount("admin")).isEqualTo(128);
        assertThat(hasPermission("admin", "admin-colonel-partner:sync")).isFalse();
    }

    private void insertRole(String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, role_code, role_name, data_scope, status, deleted)
                VALUES (?, ?, ?, 1, 1, 0)
                """, UUID.randomUUID(), roleCode, roleCode);
    }

    private int permissionCount(String roleCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code = ?
                """, Integer.class, roleCode);
    }

    private boolean hasPermission(String roleCode, String permissionCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM sys_role_permission rp
                    JOIN sys_role r ON r.id = rp.role_id
                    JOIN sys_permission p ON p.id = rp.permission_id
                    WHERE r.role_code = ? AND p.permission_code = ?
                )
                """, Boolean.class, roleCode, permissionCode));
    }
}
