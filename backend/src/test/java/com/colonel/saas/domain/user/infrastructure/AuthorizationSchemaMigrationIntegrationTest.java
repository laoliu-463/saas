package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationSchemaMigrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authorizationMigration_shouldBeIdempotentOnPostgreSQL16() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/alter-authorization-foundation-20260713.sql"));

        populator.execute(dataSource);
        populator.execute(dataSource);

        String serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version')",
                String.class);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_user'
                  AND column_name = 'authz_version'
                """, Integer.class);

        assertThat(serverVersion).startsWith("16.");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void permissionCanonicalConstraints_shouldRejectUppercaseCode() {
        assertPermissionRejected("SAMPLE:read", "sample", "read");
    }

    @Test
    void permissionCanonicalConstraints_shouldRejectExtraColon() {
        assertPermissionRejected("sample:read:extra", "sample", "read");
    }

    @Test
    void permissionCanonicalConstraints_shouldRejectEmptyComponent() {
        assertPermissionRejected("sample:", "sample", "");
    }

    @ParameterizedTest
    @MethodSource("overlongPermissionParts")
    void permissionCanonicalConstraints_shouldReject64CharacterComponent(
            String permissionCode,
            String resourceCode,
            String actionCode) {
        assertPermissionRejected(permissionCode, resourceCode, actionCode);
    }

    @Test
    void roleDomainScopeConstraint_shouldRejectDeny() {
        UUID roleId = insertRole("scope_constraint");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sys_role_domain_scope (role_id, domain_code, scope_code)
                        VALUES (?, 'sample', 'DENY')
                        """,
                roleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rolePermissionForeignKeys_shouldUseRestrictiveDeleteRulesAndBlockParentDeletion() {
        UUID roleId = insertRole("fk_role");
        UUID permissionId = insertPermission("sample", "read");
        jdbcTemplate.update("""
                        INSERT INTO sys_role_permission (role_id, permission_id)
                        VALUES (?, ?)
                        """,
                roleId,
                permissionId);

        List<String> deleteRules = jdbcTemplate.queryForList("""
                SELECT rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema
                 AND rc.constraint_name = tc.constraint_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'sys_role_permission'
                  AND tc.constraint_type = 'FOREIGN KEY'
                ORDER BY tc.constraint_name
                """, String.class);

        assertThat(deleteRules)
                .hasSize(2)
                .allSatisfy(rule -> assertThat(rule).isIn("NO ACTION", "RESTRICT"));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM sys_permission WHERE id = ?",
                permissionId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM sys_role WHERE id = ?",
                roleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_id = ?",
                Long.class,
                roleId,
                permissionId)).isEqualTo(1L);
    }

    @Test
    void authorizationCatalog_shouldExposeUuidTargetAnd500CharacterPermissionRemark() {
        String targetIdType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_authz_change_log'
                  AND column_name = 'target_id'
                """, String.class);
        Integer remarkLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_permission'
                  AND column_name = 'remark'
                """, Integer.class);

        assertThat(targetIdType).isEqualTo("uuid");
        assertThat(remarkLength).isEqualTo(500);
    }

    @Test
    void authorizationCatalog_shouldExposeRequiredUniqueAndLookupIndexes() {
        List<String> indexNames = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename IN (
                      'sys_permission',
                      'sys_role_permission',
                      'sys_role_domain_scope',
                      'sys_authz_change_log'
                  )
                """, String.class);
        String permissionCodeIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'sys_permission'
                  AND indexname = 'uk_sys_permission_code'
                """, String.class);

        assertThat(indexNames).contains(
                "uk_sys_permission_code",
                "idx_sys_permission_domain_status_deleted",
                "idx_sys_role_permission_permission",
                "idx_sys_role_domain_scope_domain_scope",
                "idx_sys_authz_change_log_target_time",
                "idx_sys_authz_change_log_actor_time");
        assertThat(permissionCodeIndex).contains("CREATE UNIQUE INDEX");
    }

    private static Stream<Arguments> overlongPermissionParts() {
        String overlongResource = "r".repeat(64);
        String overlongAction = "a".repeat(64);
        return Stream.of(
                Arguments.of(overlongResource + ":read", overlongResource, "read"),
                Arguments.of("sample:" + overlongAction, "sample", overlongAction));
    }

    private void assertPermissionRejected(
            String permissionCode,
            String resourceCode,
            String actionCode) {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sys_permission (
                            id, permission_code, domain_code, resource_code, action_code,
                            data_scope_required, status, deleted
                        ) VALUES (?, ?, 'sample', ?, ?, TRUE, 1, 0)
                        """,
                UUID.randomUUID(),
                permissionCode,
                resourceCode,
                actionCode))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertRole(String roleCode) {
        UUID roleId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO sys_role (
                            id, role_code, role_name, data_scope, status, deleted
                        ) VALUES (?, ?, ?, 1, 1, 0)
                        """,
                roleId,
                roleCode,
                roleCode);
        return roleId;
    }

    private UUID insertPermission(String resourceCode, String actionCode) {
        UUID permissionId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO sys_permission (
                            id, permission_code, domain_code, resource_code, action_code,
                            data_scope_required, status, deleted
                        ) VALUES (?, ?, ?, ?, ?, TRUE, 1, 0)
                        """,
                permissionId,
                resourceCode + ":" + actionCode,
                resourceCode,
                resourceCode,
                actionCode);
        return permissionId;
    }
}
