package com.colonel.saas.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "debug=false",
        "spring.main.banner-mode=off",
        "spring.main.log-startup-info=false",
        "spring.devtools.restart.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.domain-event.dispatch-enabled=false",
        "product.sync.activityProduct.manual-queue-drain-enabled=false",
        "douyin.webhook.replay.enabled=false",
        "app.test.seed-on-startup=false",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.boot=INFO",
        "logging.level.org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger=ERROR",
        "logging.level.org.springframework.web=INFO"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("colonel_saas_char_baseline_v2")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("db/mapper-integration-schema.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanMapperIntegrationTables() {
        applySchemaCompatibilityPatches();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    sys_authz_change_log,
                    sys_role_domain_scope,
                    sys_role_permission,
                    sys_permission,
                    sys_user_role,
                    sys_role,
                    sys_user,
                    sys_dept,
                    promotion_link,
                    performance_records,
                    colonel_partner,
                    colonel_activity,
                    product,
                    product_snapshot,
                    talent,
                    talent_claim,
                    crawler_talent_info,
                    pick_source_mapping,
                    sample_request,
                    sample_status_log,
                    colonelsettlement_order,
                    product_operation_state,
                    product_operation_log,
                    commissions,
                    system_config,
                    operation_log,
                    merchant,
                    exclusive_merchant,
                    system_config,
                    system_config_change_log,
                    domain_event_outbox,
                    domain_event_consume_log,
                    product_operation_log,
                    commissions
                RESTART IDENTITY CASCADE
                """);
    }

    /**
     * 对 Testcontainers 复用容器上可能缺失的列做幂等补齐，避免 init 脚本仅首次执行导致 schema 漂移。
     */
    private void applySchemaCompatibilityPatches() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS commissions (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    dimension_type VARCHAR(32) NOT NULL,
                    dimension_id VARCHAR(128),
                    commission_type VARCHAR(32) NOT NULL,
                    ratio NUMERIC(10, 4) NOT NULL,
                    effective_start TIMESTAMP,
                    effective_end TIMESTAMP,
                    status SMALLINT NOT NULL DEFAULT 1,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    version INT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT chk_commissions_dimension_type
                        CHECK (dimension_type IN ('global', 'activity', 'product', 'user')),
                    CONSTRAINT chk_commissions_commission_type
                        CHECK (commission_type IN ('recruiter', 'channel')),
                    CONSTRAINT chk_commissions_ratio_range
                        CHECK (ratio >= 0 AND ratio <= 1)
                )
                """);
        jdbcTemplate.execute("ALTER TABLE commissions ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_operation_log (
                    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    activity_id       VARCHAR(64) NOT NULL,
                    product_id        VARCHAR(64) NOT NULL,
                    operation_type    VARCHAR(64) NOT NULL,
                    before_status     VARCHAR(64),
                    after_status      VARCHAR(64),
                    success           BOOLEAN DEFAULT TRUE,
                    error_message     TEXT,
                    operation_payload TEXT,
                    operation_remark  VARCHAR(500),
                    operator_id       UUID,
                    operator_dept_id  UUID,
                    deleted           SMALLINT NOT NULL DEFAULT 0,
                    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by         UUID,
                    update_by         UUID
                )
                """);
        jdbcTemplate.execute("ALTER TABLE sample_request ADD COLUMN IF NOT EXISTS shipper_code VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE system_config ADD COLUMN IF NOT EXISTS config_version INT NOT NULL DEFAULT 1");
        jdbcTemplate.execute("ALTER TABLE system_config ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE system_config ADD COLUMN IF NOT EXISTS visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE system_config_change_log ADD COLUMN IF NOT EXISTS config_version INT");
        jdbcTemplate.execute(
                "ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS estimate_service_fee_expense BIGINT DEFAULT 0");
        jdbcTemplate.execute(
                "ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS effective_service_fee_expense BIGINT DEFAULT 0");
        jdbcTemplate.execute(
                "ALTER TABLE performance_records ADD COLUMN IF NOT EXISTS estimate_service_fee_expense BIGINT DEFAULT 0");
        jdbcTemplate.execute(
                "ALTER TABLE performance_records ADD COLUMN IF NOT EXISTS effective_service_fee_expense BIGINT DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS product_pic VARCHAR(512)");
        // DDD-CONFIG-004: SystemConfig entity added configVersion / enabled / visible_in_rule_center.
        // Test container init script declares these only in the second (shadowed) CREATE TABLE
        // statement, so add idempotent ALTERs to align the actual schema with the entity.
        jdbcTemplate.execute(
                "ALTER TABLE system_config ADD COLUMN IF NOT EXISTS config_version INT NOT NULL DEFAULT 1");
        jdbcTemplate.execute(
                "ALTER TABLE system_config ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE");
        jdbcTemplate.execute(
                "ALTER TABLE system_config ADD COLUMN IF NOT EXISTS visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE");
        // dashboard_performance_daily is created in production via alter-v1-gaps-20260522.sql
        // (real-pre runs the migrate-all flow). The init schema used by the test container
        // does not include it, but DashboardPerformanceSummaryService writes here from
        // OrderSyncedEventListener whenever seedAll triggers an insert. Add an idempotent
        // CREATE so the listener can run during characterization tests.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dashboard_performance_daily (
                    stat_date DATE NOT NULL PRIMARY KEY,
                    order_count BIGINT NOT NULL DEFAULT 0,
                    order_amount BIGINT NOT NULL DEFAULT 0,
                    service_fee_net BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
