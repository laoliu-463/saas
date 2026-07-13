package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationSchemaContractTest {

    private static final List<String> AUTHORIZATION_SCHEMA_FRAGMENTS = List.of(
            "authz_version bigint not null default 1",
            "create table if not exists sys_permission",
            "create table if not exists sys_role_permission",
            "create table if not exists sys_role_domain_scope",
            "create table if not exists sys_authz_change_log",
            "check (scope_code in ('self', 'group', 'all'))");

    @Test
    void authorizationSchemas_shouldContainDormantAuthorizationFoundation() throws IOException {
        List<Path> schemaFiles = List.of(
                Path.of("src/main/resources/db/alter-authorization-foundation-20260713.sql"),
                Path.of("src/main/resources/db/init-db.sql"),
                Path.of("src/test/resources/db/mapper-integration-schema.sql"));

        for (Path schemaFile : schemaFiles) {
            String sql = normalize(Files.readString(schemaFile));

            assertThat(sql)
                    .as(schemaFile + " should contain the authorization foundation")
                    .contains(AUTHORIZATION_SCHEMA_FRAGMENTS);
        }
    }

    @Test
    void migrateAll_shouldIncludeAuthorizationFoundationMigration() throws IOException {
        String migrateAll = normalize(Files.readString(
                Path.of("src/main/resources/db/migrate-all.sql")));

        assertThat(migrateAll).contains("\\i alter-authorization-foundation-20260713.sql");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
