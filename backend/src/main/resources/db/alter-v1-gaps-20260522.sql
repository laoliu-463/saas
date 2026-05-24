-- V1 gap fixes: P-05 pin, T-04/T-05 talent, performance summary (A-05)

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pinned_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pinned_by UUID;

CREATE INDEX IF NOT EXISTS idx_pos_pinned_by_until
    ON product_operation_state (pinned_by, pinned_until)
    WHERE pinned_until IS NOT NULL;

ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS talent_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS tag_updated_by UUID,
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_recipient_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS shipping_recipient_address VARCHAR(512);

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(512);

COMMENT ON COLUMN product_operation_state.pinned_until IS '招商置顶截止时间（24h）';
COMMENT ON COLUMN talent.talent_tags IS '达人标签，最多3个';
COMMENT ON COLUMN talent.tag_updated_by IS '最近一次更新达人标签的用户';

CREATE TABLE IF NOT EXISTS dashboard_performance_daily (
    stat_date DATE NOT NULL PRIMARY KEY,
    order_count BIGINT NOT NULL DEFAULT 0,
    order_amount BIGINT NOT NULL DEFAULT 0,
    service_fee_net BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE dashboard_performance_daily IS '看板日汇总（订单同步事件增量维护，A-05）';

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'promotion.copy_brief_template',
    '【{productName}】\n佣金率：{commissionRate}\n短链：{shortLink}',
    'text',
    'promotion',
    '复制讲解模板，占位符：{productName} {commissionRate} {shortLink} {pickSource}',
    1
)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_role (role_code, role_name, data_scope, status)
VALUES ('colonel_leader', '招商组长', 2, 1)
ON CONFLICT (role_code) DO NOTHING;
