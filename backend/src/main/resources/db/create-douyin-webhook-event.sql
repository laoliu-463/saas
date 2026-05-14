-- Webhook 事件收件箱：用于幂等接收、消费状态追踪和重放补偿。
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
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_douyin_webhook_event_key
    ON douyin_webhook_event(event_key)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_status
    ON douyin_webhook_event(status, create_time);

CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_type
    ON douyin_webhook_event(event_type);
