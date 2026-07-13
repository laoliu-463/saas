package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationSchemaContractTest {

    private static final String AUTHORIZATION_MIGRATION_INCLUDE =
            "\\i alter-authorization-foundation-20260713.sql";

    private static final List<Path> AUTHORIZATION_SCHEMA_FILES = List.of(
            Path.of("src/main/resources/db/alter-authorization-foundation-20260713.sql"),
            Path.of("src/main/resources/db/init-db.sql"),
            Path.of("src/test/resources/db/mapper-integration-schema.sql"));

    @Test
    void authorizationSchemas_shouldContainDormantAuthorizationFoundation() throws IOException {
        for (Path schemaFile : AUTHORIZATION_SCHEMA_FILES) {
            String sql = normalize(Files.readString(schemaFile));
            String compactSql = compact(sql);

            assertThat(sql)
                    .as(schemaFile + " should add authz_version")
                    .contains("alter table sys_user add column if not exists authz_version bigint not null default 1");

            assertPermissionTable(schemaFile, sql, compactSql);
            assertRolePermissionTable(schemaFile, sql, compactSql);
            assertRoleDomainScopeTable(schemaFile, sql, compactSql);
            assertAuthorizationChangeLogTable(schemaFile, sql, compactSql);
        }
    }

    @Test
    void migrateAll_shouldInvokeAuthorizationMigration() throws IOException {
        Path migrateAllPath = Path.of("src/main/resources/db/migrate-all.sql");
        String migrateAll = normalize(Files.readString(migrateAllPath));
        List<String> effectiveLines = Files.readAllLines(migrateAllPath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("--"))
                .toList();

        assertThat(migrateAll).contains(AUTHORIZATION_MIGRATION_INCLUDE);
        assertThat(effectiveLines).isNotEmpty();
        assertThat(effectiveLines.get(effectiveLines.size() - 1))
                .as("authorization migration should be the last effective migrate-all instruction")
                .isEqualTo(AUTHORIZATION_MIGRATION_INCLUDE);
    }

    private static void assertPermissionTable(Path schemaFile, String sql, String compactSql) {
        String table = tableDefinition(schemaFile, sql, "sys_permission");

        assertThat(table)
                .as(schemaFile + " should define all sys_permission columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "permission_code varchar(128) not null",
                        "domain_code varchar(64) not null",
                        "resource_code varchar(64) not null",
                        "action_code varchar(64) not null",
                        "data_scope_required boolean not null default false",
                        "status smallint not null default 1",
                        "deleted smallint not null default 0",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid",
                        "remark varchar(255)");

        assertThat(compact(table))
                .as(schemaFile + " should define sys_permission constraints")
                .contains(
                        "constraintck_sys_permission_statuscheck(statusin(0,1))",
                        "constraintck_sys_permission_deletedcheck(deletedin(0,1))",
                        "constraintck_sys_permission_code_partscheck(permission_code=resource_code||':'||action_codeandpermission_code=lower(permission_code))");

        assertThat(compactSql)
                .as(schemaFile + " should define sys_permission indexes")
                .contains(
                        "createuniqueindexifnotexistsuk_sys_permission_codeonsys_permission(permission_code);",
                        "createindexifnotexistsidx_sys_permission_domain_status_deletedonsys_permission(domain_code,status,deleted);");
    }

    private static void assertRolePermissionTable(Path schemaFile, String sql, String compactSql) {
        String table = tableDefinition(schemaFile, sql, "sys_role_permission");

        assertThat(table)
                .as(schemaFile + " should define sys_role_permission columns")
                .contains(
                        "role_id uuid not null",
                        "permission_id uuid not null",
                        "create_time timestamp not null default current_timestamp",
                        "create_by uuid");

        assertThat(compact(table))
                .as(schemaFile + " should define sys_role_permission keys")
                .contains(
                        "foreignkey(role_id)referencessys_role(id)ondeletecascade",
                        "foreignkey(permission_id)referencessys_permission(id)ondeletecascade",
                        "primarykey(role_id,permission_id)");

        assertThat(compactSql)
                .as(schemaFile + " should define the sys_role_permission permission index")
                .contains("createindexifnotexistsidx_sys_role_permission_permissiononsys_role_permission(permission_id);");
    }

    private static void assertRoleDomainScopeTable(Path schemaFile, String sql, String compactSql) {
        String table = tableDefinition(schemaFile, sql, "sys_role_domain_scope");

        assertThat(table)
                .as(schemaFile + " should define sys_role_domain_scope columns")
                .contains(
                        "role_id uuid not null",
                        "domain_code varchar(64) not null",
                        "scope_code varchar(16) not null",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid");

        assertThat(compact(table))
                .as(schemaFile + " should define sys_role_domain_scope keys and scope constraint")
                .contains(
                        "foreignkey(role_id)referencessys_role(id)ondeletecascade",
                        "check(scope_codein('self','group','all'))",
                        "primarykey(role_id,domain_code)");

        assertThat(compactSql)
                .as(schemaFile + " should define the sys_role_domain_scope domain index")
                .contains("createindexifnotexistsidx_sys_role_domain_scope_domain_scopeonsys_role_domain_scope(domain_code,scope_code);");
    }

    private static void assertAuthorizationChangeLogTable(Path schemaFile, String sql, String compactSql) {
        String table = tableDefinition(schemaFile, sql, "sys_authz_change_log");

        assertThat(table)
                .as(schemaFile + " should define all sys_authz_change_log columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "change_action varchar(64) not null",
                        "target_type varchar(64) not null",
                        "target_id varchar(128) not null",
                        "actor_user_id uuid",
                        "before_snapshot jsonb",
                        "after_snapshot jsonb",
                        "request_id varchar(128)",
                        "trace_id varchar(128)",
                        "changed_at timestamp not null default current_timestamp");

        assertThat(compactSql)
                .as(schemaFile + " should define sys_authz_change_log indexes")
                .contains(
                        "createindexifnotexistsidx_sys_authz_change_log_target_timeonsys_authz_change_log(target_type,target_id,changed_atdesc);",
                        "createindexifnotexistsidx_sys_authz_change_log_actor_timeonsys_authz_change_log(actor_user_id,changed_atdesc);");
    }

    private static String tableDefinition(Path schemaFile, String sql, String tableName) {
        String prefix = "create table if not exists " + tableName + " (";
        int start = sql.indexOf(prefix);
        assertThat(start)
                .as(schemaFile + " should create " + tableName)
                .isGreaterThanOrEqualTo(0);

        int end = sql.indexOf(");", start);
        assertThat(end)
                .as(schemaFile + " should terminate " + tableName + " definition")
                .isGreaterThan(start);

        return sql.substring(start, end + 2);
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String compact(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", "");
    }
}
