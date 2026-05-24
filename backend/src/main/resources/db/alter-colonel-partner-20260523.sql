-- 团长独立主数据表（P1）
CREATE TABLE IF NOT EXISTS colonel_partner (
    id UUID PRIMARY KEY,
    colonel_buyin_id VARCHAR(64) NOT NULL,
    colonel_name VARCHAR(256),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    source_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_colonel_partner_buyin_id
    ON colonel_partner (colonel_buyin_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_colonel_partner_name
    ON colonel_partner (colonel_name)
    WHERE deleted = 0;

COMMENT ON TABLE colonel_partner IS '团长合作方主数据（从活动/订单/归因映射同步，支持人工维护联系方式）';
COMMENT ON COLUMN colonel_partner.colonel_buyin_id IS '抖店团长 buyin ID';
COMMENT ON COLUMN colonel_partner.colonel_name IS '团长名称（同步可覆盖，人工维护字段除外）';
COMMENT ON COLUMN colonel_partner.contact_name IS '人工维护联系人，空值同步不得覆盖';
COMMENT ON COLUMN colonel_partner.contact_phone IS '人工维护联系电话，空值同步不得覆盖';
