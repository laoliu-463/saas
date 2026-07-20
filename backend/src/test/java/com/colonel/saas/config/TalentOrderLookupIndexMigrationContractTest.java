package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TalentOrderLookupIndexMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migrate/V20260719_002__talent_order_lookup_index.sql");
    private static final Path INIT_SCHEMA = Path.of("src/main/resources/db/init-db.sql");

    @Test
    void flywayMigration_shouldCreateTalentLookupAndRecencyIndexOnPartitionedOrders() throws IOException {
        assertThat(MIGRATION).exists();
        assertTalentLookupIndex(Files.readString(MIGRATION));
    }

    @Test
    void freshSchema_shouldKeepTheSameTalentLookupIndex() throws IOException {
        assertTalentLookupIndex(Files.readString(INIT_SCHEMA));
    }

    private static void assertTalentLookupIndex(String sql) {
        String compact = sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        assertThat(compact)
                .contains("create index if not exists idx_cso_talent_lookup_create_time")
                .contains("on colonelsettlement_order")
                .contains("coalesce(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name)")
                .contains("create_time desc")
                .contains("where deleted = 0");
    }
}
