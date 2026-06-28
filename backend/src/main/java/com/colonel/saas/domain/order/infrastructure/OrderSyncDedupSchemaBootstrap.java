package com.colonel.saas.domain.order.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单同步去重声明表引导类（ApplicationRunner）。
 *
 * <p>应用启动时自动创建 {@code order_sync_dedup_claim} 表，用于订单同步的幂等去重机制。
 * 该表以 {@code order_id} 为主键，记录每笔订单首次和最近一次同步的时间戳，
 * 以及对应的订单行 ID（{@code order_row_id}）。</p>
 *
 * <ul>
 *   <li>创建去重声明表 {@code order_sync_dedup_claim}</li>
 *   <li>创建 {@code order_row_id} 辅助索引</li>
 *   <li>从已有 {@code colonelsettlement_order} 表回填历史数据，避免存量订单重复同步</li>
 * </ul>
 *
 * <p><b>业务领域：</b>订单域 — 同步去重基础设施</p>
 * <p><b>协作关系：</b>为 {@link OrderSyncPersistenceService} 的 claim-based 去重机制提供表结构保障</p>
 *
 * @see OrderSyncPersistenceService
 */
@Slf4j
@Component
public class OrderSyncDedupSchemaBootstrap implements ApplicationRunner {

    /** JDBC 模板，用于执行 DDL 和回填 SQL */
    private final JdbcTemplate jdbcTemplate;

    public OrderSyncDedupSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动时执行：确保去重声明表存在并回填历史数据。
     *
     * <ol>
     *   <li>第一步：创建 {@code order_sync_dedup_claim} 表（若不存在），以 order_id 为主键</li>
     *   <li>第二步：创建 order_row_id 辅助索引，用于关联查询</li>
     *   <li>第三步：从 colonelsettlement_order 回填历史订单数据（取每笔订单最新一条），
     *       使用 ON CONFLICT 保证幂等</li>
     * </ol>
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        // 第一步：创建去重声明表
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS order_sync_dedup_claim (
                    order_id      VARCHAR(128) PRIMARY KEY,
                    order_row_id  UUID,
                    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        // 第二步：创建 order_row_id 辅助索引
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_order_sync_dedup_claim_row_id
                    ON order_sync_dedup_claim(order_row_id)
                """);
        if (!sourceOrderTableExists()) {
            log.warn("Skip order sync dedup claim backfill because colonelsettlement_order does not exist");
            log.info("Order sync dedup claim schema ensured");
            return;
        }
        // 第三步：从已有订单表回填历史数据（每笔订单取最新一条，幂等插入）
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

    private boolean sourceOrderTableExists() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.colonelsettlement_order') IS NOT NULL",
                Boolean.class
        ));
    }
}
