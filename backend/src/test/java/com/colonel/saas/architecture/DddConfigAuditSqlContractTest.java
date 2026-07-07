package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DddConfigAuditSqlContractTest {

    private static final List<String> CHANGE_LOG_COLUMNS = List.of(
            "id uuid",
            "config_id uuid",
            "config_key varchar(100) not null",
            "change_action varchar(20) not null",
            "old_value text",
            "new_value text",
            "source varchar(50) not null",
            "operator_id uuid",
            "changed_at timestamp not null default current_timestamp",
            "event_id uuid",
            "change_reason text",
            "config_version int");

    @Test
    void initAndTestSchemas_shouldContainConfigAuditColumns() throws IOException {
        Map<String, Path> schemaFiles = Map.of(
                "init-db", Path.of("src/main/resources/db/init-db.sql"),
                "mapper-integration-schema", Path.of("src/test/resources/db/mapper-integration-schema.sql"));

        for (Map.Entry<String, Path> entry : schemaFiles.entrySet()) {
            String sql = normalize(Files.readString(entry.getValue()));

            assertThat(sql)
                    .as(entry.getKey() + " should create system_config_change_log")
                    .contains("create table if not exists system_config_change_log");
            for (String column : CHANGE_LOG_COLUMNS) {
                assertThat(sql)
                        .as(entry.getKey() + " should contain " + column)
                        .contains(column);
            }
        }
    }

    @Test
    void migrationScripts_shouldPreserveConfigAuditBackfillColumns() throws IOException {
        String createMigration = normalize(Files.readString(
                Path.of("src/main/resources/db/alter-config-change-log-20260522.sql")));
        String eventMigration = normalize(Files.readString(
                Path.of("src/main/resources/db/alter-domain-event-config-20260523.sql")));
        String migrateAll = normalize(Files.readString(Path.of("src/main/resources/db/migrate-all.sql")));

        assertThat(createMigration).contains("create table if not exists system_config_change_log");
        assertThat(eventMigration)
                .contains("alter table system_config_change_log add column if not exists event_id uuid")
                .contains("alter table system_config_change_log add column if not exists change_reason text")
                .contains("alter table system_config_change_log add column if not exists config_version int");
        assertThat(migrateAll)
                .contains("create table if not exists system_config_change_log")
                .contains("alter table system_config_change_log add column if not exists event_id uuid")
                .contains("alter table system_config_change_log add column if not exists change_reason text")
                .contains("alter table system_config_change_log add column if not exists config_version int");
    }

    @Test
    void sqlScripts_shouldKeepConfigAuditLookupIndexes() throws IOException {
        List<Path> schemaFiles = List.of(
                Path.of("src/main/resources/db/init-db.sql"),
                Path.of("src/main/resources/db/migrate-all.sql"),
                Path.of("src/test/resources/db/mapper-integration-schema.sql"));

        for (Path schemaFile : schemaFiles) {
            String compact = compact(Files.readString(schemaFile));

            assertThat(compact)
                    .as(schemaFile + " should keep config-key audit lookup index")
                    .contains("onsystem_config_change_log(config_key,changed_atdesc)");
            assertThat(compact)
                    .as(schemaFile + " should keep operator audit lookup index")
                    .contains("onsystem_config_change_log(operator_id,changed_atdesc)");
        }
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String compact(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", "");
    }
}
