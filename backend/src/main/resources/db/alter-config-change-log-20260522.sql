-- CONFIG-01: structured system configuration change log

CREATE TABLE IF NOT EXISTS system_config_change_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id     UUID,
    config_key    VARCHAR(100) NOT NULL,
    change_action VARCHAR(20)  NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    source        VARCHAR(50)  NOT NULL,
    operator_id   UUID,
    changed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sccl_config_key_changed
    ON system_config_change_log (config_key, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_sccl_operator_changed
    ON system_config_change_log (operator_id, changed_at DESC);

COMMENT ON TABLE system_config_change_log IS '系统配置变更明细，记录旧值、新值、配置键、来源和操作者';
