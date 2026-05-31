package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ProductStateMigrationContractTest {

    private static final Path DB_DIR = Path.of("src/main/resources/db");
    private static final Path ACTIVE_COMPOSE = Path.of("../docker-compose.yml").normalize();
    private static final Path TEST_COMPOSE = Path.of("../docker-compose.test.yml").normalize();
    private static final Path REAL_PRE_COMPOSE = Path.of("../docker-compose.real-pre.yml").normalize();

    @Test
    void productStateSplitMigration_shouldBackfillStateBeforeShrinkingLegacyColumns() throws IOException {
        Path migration = DB_DIR.resolve("alter-colonel-activity-product-state-split.sql");

        assertThat(migration).exists();
        String sql = readLower(migration);
        assertThat(sql).contains("insert into product_operation_state");
        assertThat(sql).contains("update product_operation_state pos");
        assertThat(sql).contains("drop column if exists title");
        assertThat(sql).contains("drop column if exists assignee_id");
    }

    @Test
    void composeFiles_shouldMountProductStateSplitMigration() throws IOException {
        String activeCompose = Files.readString(ACTIVE_COMPOSE);
        assertMountedAfter(activeCompose,
                "30-create-colonel-order-settlement.sql",
                "31-alter-colonel-activity-product-state-split.sql");

        String testCompose = Files.readString(TEST_COMPOSE);
        assertThat(testCompose).contains("02-migrate-all.sql");

        String realPreCompose = Files.readString(REAL_PRE_COMPOSE);
        assertThat(realPreCompose).contains("99-migrate-all.sql");

        String mergedMigration = readLower(DB_DIR.resolve("migrate-all.sql"));
        assertThat(mergedMigration).contains("product_operation_state");
        assertThat(mergedMigration).contains("display_status");
    }

    private static void assertMountedAfter(String compose, String earlier, String later) {
        int earlierIndex = compose.indexOf(earlier);
        int laterIndex = compose.indexOf(later);
        assertThat(earlierIndex).isNotNegative();
        assertThat(laterIndex).isGreaterThan(earlierIndex);
    }

    private static String readLower(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}
