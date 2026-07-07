package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderFactPayloadDedupContractTest {

    @Test
    void orderFactTableShouldKeepCurrentPayloadFieldLedger() throws IOException {
        String initDb = readProjectFile("src/main/resources/db/init-db.sql");
        String orderTable = section(initDb,
                "CREATE TABLE IF NOT EXISTS colonelsettlement_order",
                ") PARTITION BY RANGE (create_time);");

        assertThat(orderTable)
                .contains(
                        "order_id                 VARCHAR(50)  NOT NULL",
                        "pick_source              VARCHAR(128)",
                        "colonel_buyin_id         BIGINT",
                        "colonel_activity_id      VARCHAR(50)",
                        "channel_user_id          UUID",
                        "colonel_user_id          UUID",
                        "talent_id                UUID",
                        "attribution_status       VARCHAR(32) DEFAULT 'UNATTRIBUTED'",
                        "extra_data               JSONB",
                        "CONSTRAINT pk_cso PRIMARY KEY (id, create_time)")
                .doesNotContain("raw_payload");

        assertThat(initDb)
                .contains(
                        "CREATE INDEX IF NOT EXISTS idx_cso_order_id ON colonelsettlement_order (order_id)",
                        "CREATE INDEX IF NOT EXISTS idx_cso_pick_source ON colonelsettlement_order (pick_source)",
                        "CREATE INDEX IF NOT EXISTS idx_cso_attribution_status ON colonelsettlement_order (attribution_status)");
    }

    @Test
    void orderEntityAndMapperShouldPersistExtraDataAsJsonbRawPayloadLedger() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java",
                "@TableField(value = \"extra_data\", typeHandler = JacksonTypeHandler.class)",
                "private Map<String, Object> extraData",
                "public Map<String, Object> getExtraData()",
                "public void setExtraData(Map<String, Object> extraData)");

        assertFileContains(
                "src/main/resources/mapper/ColonelsettlementOrderMapper.xml",
                "<result property=\"extraData\" column=\"extra_data\" typeHandler=\"com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler\"/>",
                "CAST(#{extraData, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSONB)",
                "extra_data = CAST(#{extraData, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSONB)");
    }

    @Test
    void orderSyncDedupShouldUseClaimTableBeforeInsertOrUpdate() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java",
                "int claimEffect = orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())",
                "ColonelsettlementOrder existing = orderMapper.findByOrderId(order.getOrderId())",
                "orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId())",
                "if (claimEffect <= 0) {",
                "return false;",
                "int effect = orderMapper.insertIgnoreByOrderId(order)",
                "OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order))");

        assertFileContains(
                "src/main/resources/mapper/ColonelsettlementOrderMapper.xml",
                "<insert id=\"insertIgnoreByOrderId\"",
                "WHERE NOT EXISTS (",
                "WHERE order_id = #{orderId}",
                "<update id=\"updateSyncedById\"",
                "AND version = COALESCE(#{version}, 0)");
    }

    @Test
    void orderSyncDedupSchemaShouldBeIdempotentAndBackfillLatestOrderRows() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/mapper/OrderSyncDedupClaimMapper.java",
                "INSERT INTO order_sync_dedup_claim",
                "ON CONFLICT (order_id) DO NOTHING",
                "UPDATE order_sync_dedup_claim",
                "last_seen_at = NOW()",
                "order_row_id IS DISTINCT FROM #{orderRowId}");

        assertFileContains(
                "src/main/resources/db/create-order-sync-dedup-claim.sql",
                "CREATE TABLE IF NOT EXISTS order_sync_dedup_claim",
                "order_id      VARCHAR(128) PRIMARY KEY",
                "order_row_id  UUID",
                "first_seen_at TIMESTAMP NOT NULL DEFAULT NOW()",
                "last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW()",
                "CREATE INDEX IF NOT EXISTS idx_order_sync_dedup_claim_row_id",
                "SELECT DISTINCT ON (order_id) order_id, id",
                "ORDER BY order_id, create_time DESC, update_time DESC NULLS LAST, id DESC",
                "ON CONFLICT (order_id) DO UPDATE");

        assertFileContains(
                "src/main/java/com/colonel/saas/service/OrderSyncDedupSchemaBootstrap.java",
                "CREATE TABLE IF NOT EXISTS order_sync_dedup_claim",
                "SELECT to_regclass('public.colonelsettlement_order') IS NOT NULL",
                "SELECT DISTINCT ON (order_id) order_id, id",
                "ON CONFLICT (order_id) DO UPDATE");
    }

    @Test
    void orderPayloadAndDedupExistingTestsShouldStayDiscoverable() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java",
                "persistOrder_shouldPublishOrderEventWithTalentUidAndRawExtraData",
                "order.setExtraData(Map.of(\"author_id\", \"AUTHOR-7788\", \"merchant_id\", \"MERCHANT-1\"))",
                "assertThat(event.extraData()).containsEntry(\"merchant_id\", \"MERCHANT-1\")",
                "persistOrder_shouldBeIdempotentWhenConcurrentClaimFails",
                "when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(0)",
                "verify(orderMapper, never()).insertIgnoreByOrderId(any())");

        assertFileContains(
                "src/test/java/com/colonel/saas/service/OrderSyncDedupSchemaBootstrapTest.java",
                "run_shouldEnsureOrderSyncDedupSchema",
                "run_shouldSkipBackfillWhenOrderTableDoesNotExist",
                "verify(jdbcTemplate).execute(contains(\"CREATE TABLE IF NOT EXISTS order_sync_dedup_claim\"))",
                "verify(jdbcTemplate, never()).execute(contains(\"INSERT INTO order_sync_dedup_claim\"))");
    }

    @Test
    void orderDocsShouldRecordCurrentPayloadAndDedupFieldLedger() throws IOException {
        assertFileContains(
                "../docs/领域/订单域.md",
                "订单主表 `colonelsettlement_order` 当前以 `extra_data JSONB` 保存上游原始扩展载荷",
                "`order_id` 是订单同步幂等主键输入",
                "`order_sync_dedup_claim.order_id` 为 primary key",
                "`order_row_id`、`first_seen_at`、`last_seen_at`");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);

        assertThat(source)
                .as(relativePath)
                .contains(expectedFragments);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("section start: %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("section end: %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex + end.length());
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath));
    }

    private static Path projectPath(String relativePath) {
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }
}
