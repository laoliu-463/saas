package com.colonel.saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 抖音 Webhook 事件收件箱表引导类（ApplicationRunner）。
 *
 * <p>应用启动时自动创建 {@code douyin_webhook_event} 表，作为抖音 Webhook 事件的持久化收件箱。
 * 通过 {@code event_key} 上的唯一索引（WHERE deleted=0）实现事件幂等去重。</p>
 *
 * <ul>
 *   <li>创建 {@code douyin_webhook_event} 表，包含事件类型、载荷哈希、处理状态等字段</li>
 *   <li>创建 event_key 部分唯一索引，保证未删除事件的幂等性</li>
 *   <li>创建 status+create_time 和 event_type 辅助索引，支撑事件查询和重试</li>
 * </ul>
 *
 * <p><b>业务领域：</b>订单域 — 抖音 Webhook 事件基础设施</p>
 * <p><b>协作关系：</b>为 {@link DouyinWebhookEventService} 提供事件存储表结构</p>
 *
 * @see DouyinWebhookEventService
 */
@Slf4j
@Component
public class DouyinWebhookSchemaBootstrap implements ApplicationRunner {

    /** JDBC 模板，用于执行 DDL 建表和索引语句 */
    private final JdbcTemplate jdbcTemplate;

    public DouyinWebhookSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动时执行：确保 Webhook 事件收件箱表和索引存在。
     *
     * <ol>
     *   <li>第一步：创建 {@code douyin_webhook_event} 表，包含事件标识、载荷、状态等核心字段</li>
     *   <li>第二步：创建 event_key 部分唯一索引（WHERE deleted=0），防止未删除事件重复入库</li>
     *   <li>第三步：创建 status+create_time 复合索引，支撑按状态和时间的事件查询</li>
     *   <li>第四步：创建 event_type 索引，支撑按事件类型的筛选查询</li>
     * </ol>
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        // 第一步：创建 Webhook 事件收件箱表
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS douyin_webhook_event (
                    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_key      VARCHAR(256) NOT NULL,
                    event_type     VARCHAR(128) NOT NULL,
                    payload_hash   VARCHAR(64) NOT NULL,
                    body_length    INTEGER DEFAULT 0,
                    raw_payload    TEXT,
                    status         VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
                    consume_result VARCHAR(256),
                    retry_count    INTEGER NOT NULL DEFAULT 0,
                    received_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                    processed_at   TIMESTAMP,
                    deleted        INTEGER DEFAULT 0,
                    create_time    TIMESTAMP DEFAULT NOW(),
                    update_time    TIMESTAMP DEFAULT NOW(),
                    create_by      UUID,
                    update_by      UUID
                )
                """);
        // 第二步：event_key 部分唯一索引（仅未删除记录），保证幂等
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_douyin_webhook_event_key
                    ON douyin_webhook_event(event_key)
                    WHERE deleted = 0
                """);
        // 第三步：状态+时间复合索引，支撑事件查询和重试扫描
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_status
                    ON douyin_webhook_event(status, create_time)
                """);
        // 第四步：事件类型索引，支撑按类型筛选
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_type
                    ON douyin_webhook_event(event_type)
                """);
        log.info("Douyin webhook inbox schema ensured");
    }
}
