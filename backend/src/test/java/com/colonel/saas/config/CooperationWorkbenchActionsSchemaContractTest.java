package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CooperationWorkbenchActionsSchemaContractTest {

    private static final List<Path> SCHEMA_FILES = List.of(
            Path.of("src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql"),
            Path.of("src/main/resources/db/init-db.sql"),
            Path.of("src/main/resources/db/migrate-all.sql"));

    @Test
    void allSchemaEntrypoints_shouldContainCooperationWorkbenchActionTables() throws IOException {
        for (Path schemaFile : SCHEMA_FILES) {
            assertThat(schemaFile)
                    .as("cooperation workbench schema entrypoint should exist")
                    .exists();

            String sql = normalize(Files.readString(schemaFile));
            String compactSql = compact(sql);

            assertSamplePrivateNote(schemaFile, sql, compactSql);
            assertTalentComplaint(schemaFile, sql);
            assertTalentComplaintAttachment(schemaFile, sql, compactSql);
            assertTalentComplaintReminder(schemaFile, sql, compactSql);
        }
    }

    private static void assertSamplePrivateNote(Path schemaFile, String sql, String compactSql) {
        String table = tableDefinition(schemaFile, sql, "sample_private_note");

        assertThat(table)
                .as(schemaFile + " should define sample_private_note columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "sample_request_id uuid not null",
                        "user_id uuid not null",
                        "content varchar(200) not null",
                        "version integer not null default 0",
                        "deleted smallint not null default 0",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid");
        assertThat(compact(table))
                .as(schemaFile + " should define sample_private_note foreign keys")
                .contains(
                        "foreignkey(sample_request_id)referencessample_request(id)",
                        "foreignkey(user_id)referencessys_user(id)");
        assertThat(compactSql)
                .as(schemaFile + " should enforce one active note per cooperation owner")
                .contains("createuniqueindexifnotexistsuk_sample_private_note_owner"
                        + "onsample_private_note(sample_request_id,user_id)wheredeleted=0;");
    }

    private static void assertTalentComplaint(Path schemaFile, String sql) {
        String table = tableDefinition(schemaFile, sql, "talent_complaint");

        assertThat(table)
                .as(schemaFile + " should define talent_complaint columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "sample_request_id uuid not null",
                        "talent_id uuid not null",
                        "product_id uuid not null",
                        "reporter_user_id uuid not null",
                        "reason_code varchar(64) not null",
                        "content text not null",
                        "status varchar(32) not null default 'submitted'",
                        "version integer not null default 0",
                        "deleted smallint not null default 0",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid");
        assertThat(compact(table))
                .as(schemaFile + " should define talent_complaint foreign keys")
                .contains(
                        "foreignkey(sample_request_id)referencessample_request(id)",
                        "foreignkey(talent_id)referencestalent(id)",
                        "foreignkey(product_id)referencesproduct(id)",
                        "foreignkey(reporter_user_id)referencessys_user(id)");
    }

    private static void assertTalentComplaintAttachment(
            Path schemaFile,
            String sql,
            String compactSql) {
        String table = tableDefinition(schemaFile, sql, "talent_complaint_attachment");

        assertThat(table)
                .as(schemaFile + " should define talent_complaint_attachment columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "complaint_id uuid not null",
                        "storage_key varchar(512) not null",
                        "original_name varchar(255) not null",
                        "content_type varchar(128) not null",
                        "file_size bigint not null",
                        "sha256 char(64) not null",
                        "deleted smallint not null default 0",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid");
        assertThat(compact(table))
                .as(schemaFile + " should define the attachment complaint foreign key")
                .contains("foreignkey(complaint_id)referencestalent_complaint(id)");
        assertThat(compactSql)
                .as(schemaFile + " should keep attachment storage keys unique")
                .contains("createuniqueindexifnotexistsuk_talent_complaint_attachment_storage_key"
                        + "ontalent_complaint_attachment(storage_key);");
    }

    private static void assertTalentComplaintReminder(
            Path schemaFile,
            String sql,
            String compactSql) {
        String table = tableDefinition(schemaFile, sql, "talent_complaint_reminder");

        assertThat(table)
                .as(schemaFile + " should define talent_complaint_reminder columns")
                .contains(
                        "id uuid primary key default gen_random_uuid()",
                        "complaint_id uuid not null",
                        "recipient_user_id uuid not null",
                        "read_at timestamp",
                        "version integer not null default 0",
                        "deleted smallint not null default 0",
                        "create_time timestamp not null default current_timestamp",
                        "update_time timestamp not null default current_timestamp",
                        "create_by uuid",
                        "update_by uuid");
        assertThat(compact(table))
                .as(schemaFile + " should define talent_complaint_reminder foreign keys")
                .contains(
                        "foreignkey(complaint_id)referencestalent_complaint(id)",
                        "foreignkey(recipient_user_id)referencessys_user(id)");
        assertThat(compactSql)
                .as(schemaFile + " should enforce one active reminder per complaint recipient")
                .contains("createuniqueindexifnotexistsuk_talent_complaint_reminder_recipient"
                        + "ontalent_complaint_reminder(complaint_id,recipient_user_id)wheredeleted=0;");
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
