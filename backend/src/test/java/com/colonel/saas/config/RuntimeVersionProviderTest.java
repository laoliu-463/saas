package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeVersionProviderTest {

    @Test
    void current_readsImageGitAndBothDatabaseVersionSources() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(contains("schema_migration_log"), eq(String.class)))
                .thenReturn("migrate-all.sql@abc123");
        when(jdbcTemplate.queryForObject(contains("flyway_schema_history"), eq(String.class)))
                .thenReturn("20260718.001");
        RuntimeVersionProvider provider = new RuntimeVersionProvider(
                jdbcTemplate,
                "d269914ea0041a14c5b55a49df11a70a6f975782",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );

        RuntimeVersionProvider.RuntimeVersionSnapshot snapshot = provider.current();

        assertThat(snapshot.gitSha()).isEqualTo("d269914ea0041a14c5b55a49df11a70a6f975782");
        assertThat(snapshot.imageDigest()).startsWith("sha256:");
        assertThat(snapshot.databaseMigrationVersion()).isEqualTo("migrate-all.sql@abc123");
        assertThat(snapshot.flywayVersion()).isEqualTo("20260718.001");
    }

    @Test
    void current_neverPretendsMissingDatabaseVersionIsManaged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(contains("schema_migration_log"), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("missing"));
        when(jdbcTemplate.queryForObject(contains("flyway_schema_history"), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("missing"));
        RuntimeVersionProvider provider = new RuntimeVersionProvider(jdbcTemplate, "", "");

        RuntimeVersionProvider.RuntimeVersionSnapshot snapshot = provider.current();

        assertThat(snapshot.gitSha()).isEqualTo("UNAVAILABLE");
        assertThat(snapshot.imageDigest()).isEqualTo("UNAVAILABLE");
        assertThat(snapshot.databaseMigrationVersion()).isEqualTo("NOT_MANAGED");
        assertThat(snapshot.flywayVersion()).isEqualTo("NOT_MANAGED");
    }
}
