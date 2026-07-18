package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RealPreMigrationContractTest {

    private static final Path DB_DIR = Path.of("src/main/resources/db");
    private static final Path REPO_ROOT = Path.of("..").normalize();
    private static final Path COMPOSE_FILE = Path.of("../docker-compose.real-pre.yml").normalize();

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

        int schema = compose.indexOf("11-alter-pick-source-mapping-colonel-buyin-id.sql");
        int duplicatePickSource = compose.indexOf("12-alter-pick-source-mapping-duplicate-pick-source.sql");
        int buyinBackfill = compose.indexOf("13-backfill-pick-source-mapping-colonel-buyin-id.sql");
        int promotionLinkBackfill = compose.indexOf("14-backfill-pick-source-mapping-from-promotion-link.sql");
        int attributionBackfill = compose.indexOf("15-backfill-order-attribution-colonel-reasons.sql");
        int seedMappings = compose.indexOf("16-seed-colonel-buyin-mappings.sql");
        int webhookEvent = compose.indexOf("17-create-douyin-webhook-event.sql");
        int orderDedupClaim = compose.indexOf("18-create-order-sync-dedup-claim.sql");
        int talentProfileSync = compose.indexOf("19-alter-talent-profile-sync.sql");
        int dbPerformanceContract = compose.indexOf("20-alter-db-performance-contract-20260521.sql");

        assertThat(schema).isNotNegative();
        assertThat(duplicatePickSource).isGreaterThan(schema);
        assertThat(buyinBackfill).isGreaterThan(duplicatePickSource);
        assertThat(promotionLinkBackfill).isGreaterThan(buyinBackfill);
        assertThat(attributionBackfill).isGreaterThan(promotionLinkBackfill);
        assertThat(seedMappings).isGreaterThan(attributionBackfill);
        assertThat(webhookEvent).isGreaterThan(seedMappings);
        assertThat(orderDedupClaim).isGreaterThan(webhookEvent);
        assertThat(talentProfileSync).isGreaterThan(orderDedupClaim);
        assertThat(dbPerformanceContract).isGreaterThan(talentProfileSync);
    }

    @Test
    void realPreCompose_shouldProvideNumericDouyinTokenDefaults() throws IOException {
        String compose = Files.readString(COMPOSE_FILE);

        assertThat(compose).contains("DOUYIN_TOKEN_REFRESH_THRESHOLD_SECONDS: ${DOUYIN_TOKEN_REFRESH_THRESHOLD_SECONDS:-300}");
        assertThat(compose).contains("DOUYIN_TOKEN_REDIS_LOCK_MINUTES: ${DOUYIN_TOKEN_REDIS_LOCK_MINUTES:-5}");
    }

    @Test
    void roleAwareAttributionMigration_shouldBeMountedAndAppliedByDeployGuard() throws IOException {
        Path migration = DB_DIR.resolve("alter-role-aware-promotion-link-attribution-20260716.sql");
        String migrationSql = readLower(migration);
        String migrateAll = readLower(DB_DIR.resolve("migrate-all.sql"));
        String compose = Files.readString(COMPOSE_FILE);
        String deployScript = readLower(REPO_ROOT.resolve("harness/scripts/commands/deploy-remote.ps1"));

        assertThat(migrationSql)
            .contains("add column if not exists channel_attribution_source varchar(64)")
            .contains("add column if not exists recruiter_attribution_source varchar(64)")
            .contains("add column if not exists channel_attribution_status varchar(32)")
            .contains("add column if not exists recruiter_attribution_status varchar(32)")
            .contains("add column if not exists attribution_owner_type varchar(32)")
            .contains("chk_promotion_link_attribution_owner_type")
            .contains("chk_pick_source_mapping_attribution_owner_type");
        assertThat(migrateAll).contains("\\i alter-role-aware-promotion-link-attribution-20260716.sql");
        assertThat(compose).contains("21-alter-role-aware-promotion-link-attribution-20260716.sql");
        assertThat(deployScript)
            .contains("alter-role-aware-promotion-link-attribution-20260716.sql")
            .contains("role-aware attribution schema guard")
            .contains("expected 6 columns");
        assertThat(REPO_ROOT.resolve("harness/scripts/probes/activity-query-schema.ps1")).exists();
    }

    @Test
    void environmentEntrypoints_shouldOnlyExposeTestAndRealPre() {
        assertThat(REPO_ROOT.resolve("docker-compose.test.yml")).exists();
        assertThat(REPO_ROOT.resolve("docker-compose.real-pre.yml")).exists();
        assertThat(REPO_ROOT.resolve(".env.test.example")).exists();
        assertThat(REPO_ROOT.resolve(".env.real-pre.example")).exists();
        assertThat(Path.of("src/main/resources/application-test.yml")).exists();
        assertThat(Path.of("src/main/resources/application-real-pre.yml")).exists();

        assertThat(REPO_ROOT.resolve("docker-compose.prod.yml")).doesNotExist();
        assertThat(REPO_ROOT.resolve("docker-compose.local-mock.yml.bak")).doesNotExist();
        assertThat(REPO_ROOT.resolve(".env.prod.example")).doesNotExist();
        assertThat(REPO_ROOT.resolve(".env.local-dev.example")).doesNotExist();
        assertThat(REPO_ROOT.resolve(".env.e2e.example")).doesNotExist();
        assertThat(Path.of("src/main/resources/application-prod.yml")).doesNotExist();
        assertThat(Path.of("src/main/resources/application-real.yml")).doesNotExist();
        assertThat(Path.of("src/main/resources/application-dev.yml")).doesNotExist();
        assertThat(Path.of("src/main/resources/application-local-mock.yml")).doesNotExist();
    }

    @Test
    void dbPerformanceContract_shouldProtectFinancialAndExclusiveRelationshipInvariants() throws IOException {
        String initSql = readLower(DB_DIR.resolve("init-db.sql"));
        String migrationSql = readLower(DB_DIR.resolve("alter-db-performance-contract-20260521.sql"));

        assertFinancialAndExclusiveConstraints(initSql);
        assertFinancialAndExclusiveConstraints(migrationSql);
    }

    private static void assertFinancialAndExclusiveConstraints(String sql) {
        assertThat(sql).contains("ck_exclusive_talent_non_negative_financials");
        assertThat(sql).contains("service_fee >= 0");
        assertThat(sql).contains("channel_total_fee >= 0");
        assertThat(sql).contains("ck_exclusive_merchant_non_negative_financials");
        assertThat(sql).contains("business_total_fee >= 0");
        assertThat(sql).contains("ck_commission_settlement_non_negative_financials");
        assertThat(sql).contains("total_order_amount >= 0");
        assertThat(sql).contains("commission_amount >= 0");
        assertThat(sql).contains("tech_service_fee >= 0");
        assertThat(sql).contains("net_commission >= 0");
        assertThat(sql).contains("ck_cso_non_negative_financials");
        assertThat(sql).contains("order_amount >= 0");
        assertThat(sql).contains("actual_amount >= 0");
        assertThat(sql).contains("settle_colonel_commission >= 0");
        assertThat(sql).contains("settle_colonel_tech_service_fee >= 0");
        assertThat(sql).contains("settle_second_colonel_commission >= 0");
        assertThat(sql).contains("ck_exclusive_talent_status");
        assertThat(sql).contains("ck_exclusive_merchant_status");
        assertThat(sql).contains("status in (0, 1, 2)");
        assertThat(sql).contains("fk_em_merchant");
        assertThat(sql).contains("foreign key (merchant_id) references merchant(merchant_id) on delete cascade");
    }

    private static String readLower(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}
