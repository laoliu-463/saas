package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RealPreMigrationContractTest {

    private static final Path DB_DIR = Path.of("src/main/resources/db");
    private static final Path COMPOSE_FILE = Path.of("../docker-compose.test.yml").normalize();

    @Test
    void pickSourceColonelBuyinSchemaMigration_shouldKeepBackfillInSeparateMigration() throws IOException {
        Path schemaMigration = DB_DIR.resolve("alter-pick-source-mapping-colonel-buyin-id.sql");
        Path backfillMigration = DB_DIR.resolve("backfill-pick-source-mapping-colonel-buyin-id.sql");

        String schemaSql = readLower(schemaMigration);
        assertThat(schemaSql).contains("alter table pick_source_mapping");
        assertThat(schemaSql).contains("colonel_buyin_id");
        assertThat(schemaSql).doesNotContain("update pick_source_mapping");
        assertThat(schemaSql).doesNotContain("insert into");

        assertThat(backfillMigration).exists();
        String backfillSql = readLower(backfillMigration);
        assertThat(backfillSql).contains("update pick_source_mapping");
        assertThat(backfillSql).doesNotContain("alter table");
    }

    @Test
    void unifiedCompose_shouldMountPickSourceMigrationsInDeterministicOrder() throws IOException {
        String compose = Files.readString(COMPOSE_FILE);

        int schema = compose.indexOf("13-alter-pick-source-mapping-colonel-buyin-id.sql");
        int duplicatePickSource = compose.indexOf("14-alter-pick-source-mapping-duplicate-pick-source.sql");
        int nativeSourceType = compose.indexOf("15-alter-pick-source-mapping-native-source-type.sql");
        int pickExtraLength = compose.indexOf("16-alter-pick-source-mapping-pick-extra-length.sql");
        int buyinBackfill = compose.indexOf("23-backfill-pick-source-mapping-colonel-buyin-id.sql");
        int promotionLinkBackfill = compose.indexOf("24-backfill-pick-source-mapping-from-promotion-link.sql");
        int attributionBackfill = compose.indexOf("25-backfill-order-attribution-colonel-reasons.sql");
        int seedMappings = compose.indexOf("26-seed-colonel-buyin-mappings.sql");
        int webhookEvent = compose.indexOf("27-create-douyin-webhook-event.sql");
        int orderDedupClaim = compose.indexOf("28-create-order-sync-dedup-claim.sql");

        assertThat(schema).isNotNegative();
        assertThat(duplicatePickSource).isGreaterThan(schema);
        assertThat(nativeSourceType).isGreaterThan(duplicatePickSource);
        assertThat(pickExtraLength).isGreaterThan(nativeSourceType);
        assertThat(buyinBackfill).isGreaterThan(pickExtraLength);
        assertThat(promotionLinkBackfill).isGreaterThan(buyinBackfill);
        assertThat(attributionBackfill).isGreaterThan(promotionLinkBackfill);
        assertThat(seedMappings).isGreaterThan(attributionBackfill);
        assertThat(webhookEvent).isGreaterThan(seedMappings);
        assertThat(orderDedupClaim).isGreaterThan(webhookEvent);
    }

    private static String readLower(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}
