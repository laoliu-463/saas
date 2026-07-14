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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationSchemaMigrationIntegrationTest extends BaseIntegrationTest {

    private static final String AUTHORIZATION_MIGRATION =
            "db/alter-authorization-foundation-20260713.sql";
    private static final List<String> AUTHORIZATION_TABLES = List.of(
            "sys_permission",
            "sys_role_permission",
            "sys_role_domain_scope",
            "sys_authz_change_log");
    private static final List<String> AUTHORIZATION_INDEXES = List.of(
            "uk_sys_permission_code",
            "idx_sys_permission_domain_status_deleted",
            "idx_sys_role_permission_permission",
            "idx_sys_role_domain_scope_domain_scope",
            "idx_sys_authz_change_log_target_time",
            "idx_sys_authz_change_log_actor_time");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authorizationMigration_shouldBeIdempotentOnPostgreSQL16() {
        ResourceDatabasePopulator populator = authorizationMigrationPopulator();

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
    void authorizationMigration_fromLegacyParentSchema_shouldCreateFoundationAndRemainIdempotent() {
        try (PostgreSQLContainer<?> legacyDatabase = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("authorization_legacy_migration")
                .withUsername("test")
                .withPassword("test")) {
            legacyDatabase.start();
            DriverManagerDataSource legacyDataSource = new DriverManagerDataSource();
            legacyDataSource.setDriverClassName("org.postgresql.Driver");
            legacyDataSource.setUrl(legacyDatabase.getJdbcUrl());
            legacyDataSource.setUsername(legacyDatabase.getUsername());
            legacyDataSource.setPassword(legacyDatabase.getPassword());
            JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);

            legacyJdbc.execute("CREATE TABLE sys_user (id UUID PRIMARY KEY)");
            legacyJdbc.execute("CREATE TABLE sys_role (id UUID PRIMARY KEY)");
            assertThat(authzVersionColumnCount(legacyJdbc)).isZero();
            assertThat(authorizationTableCount(legacyJdbc)).isZero();

            ResourceDatabasePopulator populator = authorizationMigrationPopulator();
            populator.execute(legacyDataSource);

            assertThat(legacyJdbc.queryForObject(
                    "SELECT current_setting('server_version')",
                    String.class)).startsWith("16.");
            assertAuthzVersionColumn(legacyJdbc);
            assertAuthorizationTables(legacyJdbc);
            assertAuthorizationConstraintsAndForeignKeys(legacyJdbc);
            assertAuthorizationCatalogColumns(legacyJdbc);
            assertAuthorizationIndexes(legacyJdbc);
            insertAndAssertFreshMigrationFacts(legacyJdbc);

            populator.execute(legacyDataSource);

            assertThat(authzVersionColumnCount(legacyJdbc)).isEqualTo(1);
            assertThat(authorizationTableCount(legacyJdbc)).isEqualTo(4);
            assertThat(authorizationNamedIndexCount(legacyJdbc)).isEqualTo(6);
            assertThat(legacyJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_role_permission",
                    Long.class)).isEqualTo(1L);
        }
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

        assertRolePermissionForeignKeys(jdbcTemplate);
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
        assertAuthorizationCatalogColumns(jdbcTemplate);
    }

    @Test
    void authorizationCatalog_shouldExposeRequiredUniqueAndLookupIndexes() {
        assertAuthorizationIndexes(jdbcTemplate);
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
        return insertPermission(jdbcTemplate, resourceCode, actionCode);
    }

    private static UUID insertPermission(
            JdbcTemplate targetJdbc,
            String resourceCode,
            String actionCode) {
        UUID permissionId = UUID.randomUUID();
        targetJdbc.update("""
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

    private static ResourceDatabasePopulator authorizationMigrationPopulator() {
        return new ResourceDatabasePopulator(new ClassPathResource(AUTHORIZATION_MIGRATION));
    }

    private static void assertAuthzVersionColumn(JdbcTemplate targetJdbc) {
        Map<String, Object> column = targetJdbc.queryForMap("""
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_user'
                  AND column_name = 'authz_version'
                """);

        assertThat(column.get("data_type")).isEqualTo("bigint");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat(column.get("column_default").toString()).contains("1");
    }

    private static void assertAuthorizationTables(JdbcTemplate targetJdbc) {
        List<String> tableNames = targetJdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                      'sys_permission',
                      'sys_role_permission',
                      'sys_role_domain_scope',
                      'sys_authz_change_log'
                  )
                """, String.class);

        assertThat(tableNames).containsExactlyInAnyOrderElementsOf(AUTHORIZATION_TABLES);
    }

    private static void assertAuthorizationConstraintsAndForeignKeys(JdbcTemplate targetJdbc) {
        List<String> constraintNames = targetJdbc.queryForList("""
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = current_schema()
                  AND t.relname IN (
                      'sys_permission',
                      'sys_role_permission',
                      'sys_role_domain_scope'
                  )
                """, String.class);

        assertThat(constraintNames).contains(
                "ck_sys_permission_status",
                "ck_sys_permission_deleted",
                "ck_sys_permission_resource_code",
                "ck_sys_permission_action_code",
                "ck_sys_permission_code_parts",
                "fk_sys_role_permission_role",
                "fk_sys_role_permission_permission",
                "fk_sys_role_domain_scope_role",
                "ck_sys_role_domain_scope_scope");
        assertRolePermissionForeignKeys(targetJdbc);
    }

    private static void assertRolePermissionForeignKeys(JdbcTemplate targetJdbc) {
        List<Map<String, Object>> foreignKeys = targetJdbc.queryForList("""
                SELECT tc.constraint_name, rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema
                 AND rc.constraint_name = tc.constraint_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'sys_role_permission'
                  AND tc.constraint_type = 'FOREIGN KEY'
                ORDER BY tc.constraint_name
                """);

        assertThat(foreignKeys)
                .extracting(row -> row.get("constraint_name"))
                .containsExactlyInAnyOrder(
                        "fk_sys_role_permission_role",
                        "fk_sys_role_permission_permission");
        assertThat(foreignKeys)
                .extracting(row -> row.get("delete_rule"))
                .hasSize(2)
                .allSatisfy(rule -> assertThat(rule).isIn("NO ACTION", "RESTRICT"));
    }

    private static void assertAuthorizationCatalogColumns(JdbcTemplate targetJdbc) {
        String targetIdType = targetJdbc.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_authz_change_log'
                  AND column_name = 'target_id'
                """, String.class);
        Integer remarkLength = targetJdbc.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_permission'
                  AND column_name = 'remark'
                """, Integer.class);

        assertThat(targetIdType).isEqualTo("uuid");
        assertThat(remarkLength).isEqualTo(500);
    }

    private static void assertAuthorizationIndexes(JdbcTemplate targetJdbc) {
        List<String> indexNames = targetJdbc.queryForList("""
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
        String permissionCodeIndex = targetJdbc.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'sys_permission'
                  AND indexname = 'uk_sys_permission_code'
                """, String.class);

        assertThat(indexNames).contains(AUTHORIZATION_INDEXES.toArray(String[]::new));
        assertThat(permissionCodeIndex).contains("CREATE UNIQUE INDEX");
    }

    private static void insertAndAssertFreshMigrationFacts(JdbcTemplate targetJdbc) {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        targetJdbc.update("INSERT INTO sys_user (id) VALUES (?)", userId);
        targetJdbc.update("INSERT INTO sys_role (id) VALUES (?)", roleId);
        UUID permissionId = insertPermission(targetJdbc, "sample", "read");
        targetJdbc.update("""
                        INSERT INTO sys_role_permission (role_id, permission_id)
                        VALUES (?, ?)
                        """,
                roleId,
                permissionId);
        targetJdbc.update("""
                        INSERT INTO sys_role_domain_scope (role_id, domain_code, scope_code)
                        VALUES (?, 'sample', 'GROUP')
                        """,
                roleId);

        assertThat(targetJdbc.queryForObject(
                "SELECT authz_version FROM sys_user WHERE id = ?",
                Long.class,
                userId)).isEqualTo(1L);
        assertThat(targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_id = ?",
                Long.class,
                roleId,
                permissionId)).isEqualTo(1L);
        assertThatThrownBy(() -> targetJdbc.update("""
                        INSERT INTO sys_role_domain_scope (role_id, domain_code, scope_code)
                        VALUES (?, 'order', 'DENY')
                        """,
                roleId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> targetJdbc.update("""
                        INSERT INTO sys_role_permission (role_id, permission_id)
                        VALUES (?, ?)
                        """,
                UUID.randomUUID(),
                permissionId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static int authzVersionColumnCount(JdbcTemplate targetJdbc) {
        return targetJdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'sys_user'
                  AND column_name = 'authz_version'
                """, Integer.class);
    }

    private static int authorizationTableCount(JdbcTemplate targetJdbc) {
        return targetJdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                      'sys_permission',
                      'sys_role_permission',
                      'sys_role_domain_scope',
                      'sys_authz_change_log'
                  )
                """, Integer.class);
    }

    private static int authorizationNamedIndexCount(JdbcTemplate targetJdbc) {
        return targetJdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname IN (
                      'uk_sys_permission_code',
                      'idx_sys_permission_domain_status_deleted',
                      'idx_sys_role_permission_permission',
                      'idx_sys_role_domain_scope_domain_scope',
                      'idx_sys_authz_change_log_target_time',
                      'idx_sys_authz_change_log_actor_time'
                  )
                """, Integer.class);
    }
}
