CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS sys_dept (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    dept_code VARCHAR(50) NOT NULL UNIQUE,
    dept_name VARCHAR(100) NOT NULL,
    dept_type VARCHAR(32) NOT NULL DEFAULT 'department',
    leader_user_id UUID,
    leader VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    remark VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sys_dept_parent_id ON sys_dept(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_dept_deleted ON sys_dept(deleted);
CREATE INDEX IF NOT EXISTS idx_sys_dept_sort_name ON sys_dept(sort_order, dept_name);

CREATE TABLE IF NOT EXISTS sys_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    dept_id UUID,
    channel_code VARCHAR(16) NOT NULL UNIQUE,
    status SMALLINT NOT NULL DEFAULT 1,
    force_password_change BOOLEAN NOT NULL DEFAULT FALSE,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_user_dept_id ON sys_user(dept_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_channel_code ON sys_user(channel_code);
CREATE INDEX IF NOT EXISTS idx_sys_user_deleted ON sys_user(deleted);

CREATE TABLE IF NOT EXISTS promotion_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(50) NOT NULL,
    activity_id VARCHAR(50),
    talent_id VARCHAR(50),
    talent_name VARCHAR(200),
    channel_user_id UUID,
    channel_user_name VARCHAR(100),
    original_product_url TEXT,
    promotion_url TEXT,
    short_url TEXT,
    doukouling TEXT,
    pick_source VARCHAR(128),
    pick_extra VARCHAR(128),
    link_status VARCHAR(20) DEFAULT 'ACTIVE',
    expire_time TIMESTAMP,
    raw_response JSONB,
    operator_id UUID,
    operator_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_promotion_link_pick_source ON promotion_link(pick_source);
CREATE INDEX IF NOT EXISTS idx_promotion_link_channel_user ON promotion_link(channel_user_id);

CREATE TABLE IF NOT EXISTS performance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id VARCHAR(50) NOT NULL,
    order_row_id UUID,
    default_channel_user_id UUID,
    default_recruiter_user_id UUID,
    final_channel_user_id UUID,
    final_recruiter_user_id UUID,
    channel_attribution VARCHAR(32),
    recruiter_attribution VARCHAR(32),
    talent_id UUID,
    partner_id BIGINT,
    product_id VARCHAR(50),
    activity_id VARCHAR(50),
    pay_amount BIGINT NOT NULL DEFAULT 0,
    settle_amount BIGINT NOT NULL DEFAULT 0,
    estimate_service_fee BIGINT NOT NULL DEFAULT 0,
    effective_service_fee BIGINT NOT NULL DEFAULT 0,
    estimate_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    effective_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    estimate_service_profit BIGINT NOT NULL DEFAULT 0,
    effective_service_profit BIGINT NOT NULL DEFAULT 0,
    estimate_recruiter_commission BIGINT NOT NULL DEFAULT 0,
    effective_recruiter_commission BIGINT NOT NULL DEFAULT 0,
    estimate_channel_commission BIGINT NOT NULL DEFAULT 0,
    effective_channel_commission BIGINT NOT NULL DEFAULT 0,
    estimate_gross_profit BIGINT NOT NULL DEFAULT 0,
    effective_gross_profit BIGINT NOT NULL DEFAULT 0,
    recruiter_commission_rate NUMERIC(8, 4),
    channel_commission_rate NUMERIC(8, 4),
    order_status SMALLINT,
    settle_time TIMESTAMP,
    order_create_time TIMESTAMP,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    is_reversed BOOLEAN NOT NULL DEFAULT FALSE,
    calculation_version INTEGER NOT NULL DEFAULT 1,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_performance_records_order_id UNIQUE (order_id)
);

CREATE TABLE IF NOT EXISTS colonel_partner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    colonel_buyin_id VARCHAR(64) NOT NULL,
    colonel_name VARCHAR(256),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    avatar_url VARCHAR(512),
    contact_wechat VARCHAR(100),
    contact_remark TEXT,
    source VARCHAR(32),
    first_seen_at TIMESTAMP,
    last_sync_at TIMESTAMP,
    manual_contact_updated_at TIMESTAMP,
    manual_contact_updated_by VARCHAR(64),
    raw_payload JSONB,
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
