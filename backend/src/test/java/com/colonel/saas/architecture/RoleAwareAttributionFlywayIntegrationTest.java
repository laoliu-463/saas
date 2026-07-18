package com.colonel.saas.architecture;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 用真实 PostgreSQL 验证旧结构升级、新库基线和重复迁移。 */
class RoleAwareAttributionFlywayIntegrationTest {

    @Test
    void legacySchemaShouldUpgradeAndRepeatedMigrationShouldRemainIdempotent() {
        try (PostgreSQLContainer<?> database = postgres("role_aware_legacy")) {
            database.start();
            JdbcTemplate jdbc = jdbc(database);
            createLegacySchema(jdbc);

            Flyway flyway = flyway(database);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertRoleAwareColumns(jdbc, "colonelsettlement_order");
            assertRoleAwareColumns(jdbc, "colonelsettlement_order_202607");
            assertThat(columnCount(jdbc, "promotion_link", "attribution_owner_type")).isEqualTo(1);
            assertThat(columnCount(jdbc, "pick_source_mapping", "attribution_owner_type")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Long.class))
                    .isEqualTo(3L);
        }
    }

    @Test
    void newDatabaseWithProductionInitSchemaShouldBeBaselinedAndMigrated() throws Exception {
        try (PostgreSQLContainer<?> database = postgres("role_aware_new")) {
            database.start();
            JdbcTemplate jdbc = jdbc(database);
            database.copyFileToContainer(
                    MountableFile.forClasspathResource("db/init-db.sql"), "/tmp/init-db.sql");
            var init = database.execInContainer(
                    "sh", "-lc",
                    "ADMIN_PASSWORD='integration-only-password' "
                            + "psql -v ON_ERROR_STOP=1 -U " + database.getUsername()
                            + " -d " + database.getDatabaseName() + " -f /tmp/init-db.sql");
            assertThat(init.getExitCode())
                    .as("production init-db.sql stderr: %s", init.getStderr())
                    .isZero();

            Flyway flyway = flyway(database);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertRoleAwareColumns(jdbc, "colonelsettlement_order");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Long.class))
                    .isEqualTo(3L);
        }
    }

    private static PostgreSQLContainer<?> postgres(String databaseName) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(databaseName)
                .withUsername("test")
                .withPassword("test");
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer<?> database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(database.getJdbcUrl());
        dataSource.setUsername(database.getUsername());
        dataSource.setPassword(database.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static Flyway flyway(PostgreSQLContainer<?> database) {
        DataSource dataSource = new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migrate")
                .baselineOnMigrate(true)
                .baselineVersion("20260717")
                .baselineDescription("pre-flyway-schema")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    private static void createLegacySchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE colonelsettlement_order (id UUID, create_time TIMESTAMP NOT NULL) PARTITION BY RANGE (create_time)");
        jdbc.execute("CREATE TABLE colonelsettlement_order_202607 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-07-01') TO ('2026-08-01')");
        jdbc.execute("CREATE TABLE promotion_link (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE pick_source_mapping (id UUID PRIMARY KEY)");
    }

    private static void assertRoleAwareColumns(JdbcTemplate jdbc, String table) {
        assertThat(columnCount(jdbc, table, "channel_attribution_status")).isEqualTo(1);
        assertThat(columnCount(jdbc, table, "recruiter_attribution_status")).isEqualTo(1);
        assertThat(columnCount(jdbc, table, "channel_attribution_source")).isEqualTo(1);
        assertThat(columnCount(jdbc, table, "recruiter_attribution_source")).isEqualTo(1);
    }

    private static int columnCount(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
    }
}
