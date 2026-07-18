package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 防止实体、Mapper、初始化脚本和正式迁移再次出现字段漂移。 */
class RoleAwareAttributionSchemaContractTest {

    private static final List<String> ORDER_COLUMNS = List.of(
            "channel_attribution_source",
            "channel_attribution_status",
            "recruiter_attribution_source",
            "recruiter_attribution_status");

    @Test
    void entityMapperAndSqlShouldDeclareTheSameRoleAwareColumns() throws IOException {
        Path root = findBackendRoot();
        String orderEntity = read(root.resolve("src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java"));
        String pickSourceEntity = read(root.resolve("src/main/java/com/colonel/saas/entity/PickSourceMapping.java"));
        String promotionLinkEntity = read(root.resolve("src/main/java/com/colonel/saas/entity/PromotionLink.java"));
        String mapper = read(root.resolve("src/main/resources/mapper/ColonelsettlementOrderMapper.xml"));
        String init = read(root.resolve("src/main/resources/db/init-db.sql"));
        String testSchema = read(root.resolve("src/test/resources/db/mapper-integration-schema.sql"));
        String migrateAll = read(root.resolve("src/main/resources/db/migrate-all.sql"));
        String legacyMigration = read(root.resolve(
                "src/main/resources/db/alter-role-aware-promotion-link-attribution-20260716.sql"));
        String migration = read(root.resolve(
                "src/main/resources/db/migrate/V20260718_001__role_aware_attribution_schema.sql"));
        String activityMigration = read(root.resolve(
                "src/main/resources/db/migrate/V20260718_002__activity_status_sync_schema.sql"));

        ORDER_COLUMNS.forEach(column -> {
            assertThat(init).as("init-db.sql must declare " + column).contains(column);
            assertThat(testSchema).as("test schema must declare " + column).contains(column);
            assertThat(migration).as("formal migration must declare " + column).contains(column);
        });
        assertThat(init).contains("activity_status_synced_at");
        assertThat(testSchema).contains("activity_status_synced_at");
        assertThat(activityMigration).contains("ADD COLUMN IF NOT EXISTS activity_status_synced_at TIMESTAMP");
        assertThat(init).contains("CREATE TABLE IF NOT EXISTS promotion_link");
        assertThat(orderEntity).contains("channelAttributionSource", "recruiterAttributionSource");
        assertThat(mapper).contains(
                "channel_attribution_source", "recruiter_attribution_source",
                "#{channelAttributionSource}", "#{recruiterAttributionSource}");
        assertThat(pickSourceEntity).contains("attribution_owner_type", "attributionOwnerType");
        assertThat(promotionLinkEntity).contains("attribution_owner_type", "attributionOwnerType");
        assertThat(init).contains("chk_pick_source_mapping_attribution_owner_type");
        assertThat(testSchema).contains(
                "chk_pick_source_mapping_attribution_owner_type",
                "chk_promotion_link_attribution_owner_type");
        assertThat(migrateAll).contains("\\i alter-role-aware-promotion-link-attribution-20260716.sql");
        ORDER_COLUMNS.forEach(column ->
                assertThat(legacyMigration).as("legacy aggregate migration must declare " + column).contains(column));
        assertThat(migrateAll).doesNotContain("\\i migrate/");
        assertThat(migration).contains(
                "ADD COLUMN IF NOT EXISTS",
                "to_regclass('public.promotion_link')",
                "to_regclass('public.pick_source_mapping')");
    }

    @Test
    void migrationShouldBeAdditiveAndIdempotentByConstruction() throws IOException {
        String migration = read(findBackendRoot().resolve(
                "src/main/resources/db/migrate/V20260718_001__role_aware_attribution_schema.sql"));
        assertThat(migration).containsOnlyOnce("ALTER TABLE IF EXISTS public.colonelsettlement_order");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS");
        assertThat(migration).doesNotContain("DROP TABLE", "DROP COLUMN", "TRUNCATE");
    }

    private static Path findBackendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = cwd; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("Cannot find backend root from " + cwd);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
