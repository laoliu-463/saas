-- 配置域：领域事件 Outbox + 消费幂等 + 配置表扩展

CREATE TABLE IF NOT EXISTS domain_event_outbox (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128),
    event_version   INT          NOT NULL DEFAULT 1,
    payload         JSONB        NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    occurred_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    created_by      VARCHAR(64),
    trace_id        VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_status_time
    ON domain_event_outbox (status, occurred_at);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_type_time
    ON domain_event_outbox (event_type, occurred_at);

COMMENT ON TABLE domain_event_outbox IS '领域事件 Outbox，V1 本进程分发，后续可接 MQ';

CREATE TABLE IF NOT EXISTS domain_event_consume_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    error_message TEXT,
    consumed_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_domain_event_consume UNIQUE (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_consume_event
    ON domain_event_consume_log (event_id);

COMMENT ON TABLE domain_event_consume_log IS '领域事件消费幂等记录';

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS config_version INT NOT NULL DEFAULT 1;

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS event_id UUID;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS change_reason TEXT;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS config_version INT;

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status, visible_in_rule_center)
VALUES
    ('auth.login_max_failures', '5', 'int', 'security', '登录失败锁定次数', 1, TRUE),
    ('auth.login_lock_minutes', '15', 'int', 'security', '登录锁定时长（分钟）', 1, TRUE),
    ('promotion.copy_brief_template',
     '【抖音】{product_name}\n【佣金率】{commission_rate}\n【推广链接】{promotion_link}',
     'string', 'promotion', '复制讲解模板', 1, TRUE)
ON CONFLICT (config_key) DO NOTHING;
