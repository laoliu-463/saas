-- V2 配置域：差异化提成表 + 达人预设标签库

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
);

CREATE INDEX IF NOT EXISTS idx_commissions_dimension_lookup
    ON commissions (dimension_type, dimension_id, commission_type, status, deleted);

CREATE INDEX IF NOT EXISTS idx_commissions_effective_window
    ON commissions (effective_start, effective_end);

COMMENT ON TABLE commissions IS 'V2 差异化提成规则（global/activity/product/user × recruiter/channel）';

ALTER TABLE commissions ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'talent.preset_tags',
    '["美妆","高转化","服饰","食品","母婴","家居","数码","本地生活"]',
    'json',
    'talent',
    '达人预设标签库',
    1
)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'promotion.pick_extra_rule',
    '{"format":"channel_{channel_code}","encode":"none"}',
    'json',
    'promotion',
    'pick_extra 生成规则',
    1
)
ON CONFLICT (config_key) DO NOTHING;
