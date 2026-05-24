-- 商品域增强：展示规则审计/强制展示、Outbox 扩展、团长主数据扩展、寄样外部字段

-- 1. 展示规则版本
CREATE TABLE IF NOT EXISTS product_display_rule_version (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_no   INTEGER      NOT NULL UNIQUE,
    config_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO product_display_rule_version (version_no, config_json, enabled, created_by)
VALUES (
    3,
    '{"protectionMonthsDefault":3,"sortOrder":["FORCE","ADS","COMMISSION","SERVICE_FEE","SHELF_TIME","RELATION_ID"],"advantageOverride":true}'::jsonb,
    TRUE,
    'system'
)
ON CONFLICT (version_no) DO NOTHING;

-- 2. 展示规则审计
CREATE TABLE IF NOT EXISTS product_display_audit_log (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id             VARCHAR(64)  NOT NULL,
    old_relation_id        UUID,
    new_relation_id        UUID,
    candidate_relation_ids JSONB,
    action_type            VARCHAR(64)  NOT NULL,
    selected_reason        VARCHAR(128),
    hidden_reason          VARCHAR(128),
    rule_version           INTEGER      NOT NULL,
    operator_type          VARCHAR(32)  NOT NULL,
    operator_id            VARCHAR(64),
    detail_json            JSONB,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_display_audit_product_time
    ON product_display_audit_log (product_id, created_at DESC);

-- 3. product_operation_state 扩展
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS display_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS first_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS display_rule_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS force_display BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS force_display_by UUID,
    ADD COLUMN IF NOT EXISTS force_display_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS force_display_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS display_priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS manual_disabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pos_one_displaying_per_product
    ON product_operation_state (product_id)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';

CREATE INDEX IF NOT EXISTS idx_pos_product_display_status
    ON product_operation_state (product_id, display_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pos_displaying_library
    ON product_operation_state (display_status, selected_to_library)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';

-- 4. domain_event_outbox 扩展（幂等键、重试调度）
ALTER TABLE domain_event_outbox
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS headers JSONB,
    ADD COLUMN IF NOT EXISTS max_retry INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_event_outbox_event_key
    ON domain_event_outbox (event_key)
    WHERE event_key IS NOT NULL;

-- 5. colonel_partner 扩展
CREATE TABLE IF NOT EXISTS colonel_partner (
    id UUID PRIMARY KEY,
    colonel_buyin_id VARCHAR(64) NOT NULL,
    colonel_name VARCHAR(256),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    source_updated_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
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

ALTER TABLE colonel_partner
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by UUID,
    ADD COLUMN IF NOT EXISTS update_by UUID,
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS contact_wechat VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_remark TEXT,
    ADD COLUMN IF NOT EXISTS source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS manual_contact_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS manual_contact_updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS raw_payload JSONB;

-- 6. sample_request 外部寄样字段
ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS apply_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_apply_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS external_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_error_message TEXT,
    ADD COLUMN IF NOT EXISTS external_raw_payload JSONB;

CREATE INDEX IF NOT EXISTS idx_sample_request_apply_source
    ON sample_request (apply_source)
    WHERE deleted = 0;
