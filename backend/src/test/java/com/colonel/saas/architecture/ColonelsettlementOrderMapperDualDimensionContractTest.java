package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构守门人：保证 {@code ColonelsettlementOrderMapper.xml} 把双维度归属状态
 * 真正持久化到 SQL，而不只停留在 transient 实体字段。
 *
 * <p>这是 DDD-PERFORMANCE-DUAL-ATTRIBUTION-STATUS 升级的契约测试：
 * 1. Mapper XML 的 resultMap 必须映射两个新列；
 * 2. insertIgnoreByOrderId 必须写入两个新列；
 * 3. updateSyncedById 必须更新两个新列；
 * 4. findPageWithScope SELECT 必须投影两个新列。</p>
 */
class ColonelsettlementOrderMapperDualDimensionContractTest {

    private static final Path BACKEND_ROOT = findBackendRoot();
    private static final Path MAPPER = BACKEND_ROOT.resolve(
            "src/main/resources/mapper/ColonelsettlementOrderMapper.xml");
    private static final Pattern CHANNEL_STATUS = Pattern.compile(
            "(?i)(property|column)\\s*=\\s*\\\"channel[_a-z]*attribution[_a-z]*status\\\"");
    private static final Pattern RECRUITER_STATUS = Pattern.compile(
            "(?i)(property|column)\\s*=\\s*\\\"recruiter[_a-z]*attribution[_a-z]*status\\\"");
    private static final Pattern CHANNEL_INSERT = Pattern.compile(
            "#\\{channelAttributionStatus\\}");
    private static final Pattern RECRUITER_INSERT = Pattern.compile(
            "#\\{recruiterAttributionStatus\\}");
    private static final Pattern CHANNEL_SELECT = Pattern.compile(
            "(?i)co\\.channel_attribution_status");
    private static final Pattern RECRUITER_SELECT = Pattern.compile(
            "(?i)co\\.recruiter_attribution_status");

    @Test
    void mapperResultMapShouldExposeBothAttributionStatusColumns() throws IOException {
        String xml = read(MAPPER);
        assertThat(CHANNEL_STATUS.matcher(xml).results().count())
                .as("Mapper resultMap must map channel_attribution_status column")
                .isGreaterThanOrEqualTo(1);
        assertThat(RECRUITER_STATUS.matcher(xml).results().count())
                .as("Mapper resultMap must map recruiter_attribution_status column")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void insertIgnoreByOrderIdShouldBindBothAttributionStatusValues() throws IOException {
        String xml = read(MAPPER);
        assertThat(CHANNEL_INSERT.matcher(xml).results().count())
                .as("insertIgnoreByOrderId must include #{channelAttributionStatus}")
                .isGreaterThanOrEqualTo(1);
        assertThat(RECRUITER_INSERT.matcher(xml).results().count())
                .as("insertIgnoreByOrderId must include #{recruiterAttributionStatus}")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void updateSyncedByIdShouldUpdateBothAttributionStatusColumns() throws IOException {
        String xml = read(MAPPER);
        assertThat(xml).contains("channel_attribution_status = #{channelAttributionStatus}");
        assertThat(xml).contains("recruiter_attribution_status = #{recruiterAttributionStatus}");
    }

    @Test
    void findPageWithScopeShouldProjectBothAttributionStatusColumns() throws IOException {
        String xml = read(MAPPER);
        assertThat(CHANNEL_SELECT.matcher(xml).results().count())
                .as("findPageWithScope must project co.channel_attribution_status")
                .isGreaterThanOrEqualTo(1);
        assertThat(RECRUITER_SELECT.matcher(xml).results().count())
                .as("findPageWithScope must project co.recruiter_attribution_status")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void integrationSchemaAndMigrationSqlShouldDeclareBothColumns() throws IOException {
        Path integrationSchema = BACKEND_ROOT.resolve(
                "src/test/resources/db/mapper-integration-schema.sql");
        if (Files.exists(integrationSchema)) {
            String schema = read(integrationSchema);
            assertThat(schema).contains("channel_attribution_status");
            assertThat(schema).contains("recruiter_attribution_status");
            assertThat(schema).doesNotContain("channel_attribution_status VARCHAR(32) DEFAULT");
            assertThat(schema).doesNotContain("recruiter_attribution_status VARCHAR(32) DEFAULT");
        }

        Path migration = findDualDimensionMigration();
        assertThat(migration)
                .as("expected an alter-cso-dual-attribution-status migration script")
                .isNotNull();
        String sql = read(migration);
        assertThat(sql).contains("channel_attribution_status");
        assertThat(sql).contains("recruiter_attribution_status");
        assertThat(sql).contains("ALTER COLUMN channel_attribution_status DROP DEFAULT");
        assertThat(sql).contains("ALTER COLUMN recruiter_attribution_status DROP DEFAULT");
        assertThat(sql).contains("channel_user_id IS NOT NULL");
        assertThat(sql).contains("colonel_user_id IS NOT NULL");
        assertThat(sql).doesNotContain("WHERE channel_attribution_status IS NULL");
        assertThat(sql).doesNotContain("WHERE recruiter_attribution_status IS NULL");
    }

    @Test
    void migrateAllShouldInvokeDualDimensionAttributionMigration() throws IOException {
        Path migrateAll = BACKEND_ROOT.resolve("src/main/resources/db/migrate-all.sql");
        String sql = read(migrateAll);
        assertThat(sql).contains("\\i alter-cso-dual-attribution-status-20260716.sql");
    }

    private static Path findBackendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (p.resolve("src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java").toFile().exists()) {
                return p;
            }
        }
        throw new IllegalStateException("Cannot find backend root from " + cwd);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findDualDimensionMigration() {
        Path dbDir = BACKEND_ROOT.resolve("src/main/resources/db");
        if (!Files.exists(dbDir)) {
            return null;
        }
        try (var stream = Files.walk(dbDir)) {
            List<Path> matches = stream
                    .filter(p -> p.getFileName().toString().contains("dual-attribution-status"))
                    .toList();
            if (matches.isEmpty()) {
                return null;
            }
            return matches.get(0);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
