package com.colonel.saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderSyncDedupSchemaBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public OrderSyncDedupSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS order_sync_dedup_claim (
                    order_id      VARCHAR(128) PRIMARY KEY,
                    order_row_id  UUID,
                    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_order_sync_dedup_claim_row_id
                    ON order_sync_dedup_claim(order_row_id)
                """);
        jdbcTemplate.execute("""
                INSERT INTO order_sync_dedup_claim(order_id, order_row_id, first_seen_at, last_seen_at)
                SELECT order_id, id, NOW(), NOW()
                FROM (
                    SELECT DISTINCT ON (order_id) order_id, id
                    FROM colonelsettlement_order
                    WHERE deleted = 0
                      AND order_id IS NOT NULL
                      AND btrim(order_id) <> ''
                    ORDER BY order_id, create_time DESC, update_time DESC NULLS LAST, id DESC
                ) latest
                ON CONFLICT (order_id) DO UPDATE
                SET order_row_id = COALESCE(order_sync_dedup_claim.order_row_id, EXCLUDED.order_row_id),
                    last_seen_at = NOW()
                """);
        log.info("Order sync dedup claim schema ensured");
    }
}
