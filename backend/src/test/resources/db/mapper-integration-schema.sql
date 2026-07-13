CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
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

CREATE TABLE IF NOT EXISTS sys_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    data_scope SMALLINT NOT NULL DEFAULT 1,
    permissions JSONB,
    menu_config JSONB,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    remark VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

-- Dormant authorization foundation. No role-permission seed data is defined here.

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS authz_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS sys_permission (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code     VARCHAR(128) NOT NULL,
    domain_code         VARCHAR(64) NOT NULL,
    resource_code       VARCHAR(64) NOT NULL,
    action_code         VARCHAR(64) NOT NULL,
    data_scope_required BOOLEAN NOT NULL DEFAULT FALSE,
    status              SMALLINT NOT NULL DEFAULT 1,
    deleted             SMALLINT NOT NULL DEFAULT 0,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    remark              VARCHAR(255),
    CONSTRAINT ck_sys_permission_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_sys_permission_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_sys_permission_code_parts CHECK (
        permission_code = resource_code || ':' || action_code
        AND permission_code = LOWER(permission_code)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_code
    ON sys_permission(permission_code);
CREATE INDEX IF NOT EXISTS idx_sys_permission_domain_status_deleted
    ON sys_permission(domain_code, status, deleted);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    CONSTRAINT fk_sys_role_permission_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_permission_permission
    ON sys_role_permission(permission_id);

CREATE TABLE IF NOT EXISTS sys_role_domain_scope (
    role_id     UUID NOT NULL,
    domain_code VARCHAR(64) NOT NULL,
    scope_code  VARCHAR(16) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   UUID,
    update_by   UUID,
    CONSTRAINT fk_sys_role_domain_scope_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT ck_sys_role_domain_scope_scope
        CHECK (scope_code IN ('SELF', 'GROUP', 'ALL')),
    PRIMARY KEY (role_id, domain_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_domain_scope_domain_scope
    ON sys_role_domain_scope(domain_code, scope_code);

CREATE TABLE IF NOT EXISTS sys_authz_change_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_action   VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    target_id       VARCHAR(128) NOT NULL,
    actor_user_id   UUID,
    before_snapshot JSONB,
    after_snapshot  JSONB,
    request_id      VARCHAR(128),
    trace_id        VARCHAR(128),
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_authz_change_log_target_time
    ON sys_authz_change_log(target_type, target_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_authz_change_log_actor_time
    ON sys_authz_change_log(actor_user_id, changed_at DESC);

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

CREATE TABLE IF NOT EXISTS colonel_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(50) NOT NULL UNIQUE,
    activity_name VARCHAR(500),
    activity_type VARCHAR(20),
    shop_id BIGINT,
    shop_name VARCHAR(200),
    colonel_buyin_id BIGINT,
    commission_rate NUMERIC(8,4),
    service_rate NUMERIC(8,4),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    activity_status_code INTEGER,
    activity_status_text VARCHAR(64),
    recruiter_user_id UUID,
    recruiter_dept_id UUID,
    assigned_at TIMESTAMP,
    assigned_by UUID,
    months_of_protection INT,
    last_sync_at TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    extra_data JSONB
);

CREATE TABLE IF NOT EXISTS product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(50) NOT NULL UNIQUE,
    outer_product_id VARCHAR(50),
    name VARCHAR(500) NOT NULL,
    description TEXT,
    market_price BIGINT,
    discount_price BIGINT,
    cover VARCHAR(1000),
    detail_url VARCHAR(1000),
    first_cid BIGINT,
    second_cid BIGINT,
    third_cid BIGINT,
    fourth_cid BIGINT,
    category_detail JSONB,
    pics JSONB,
    spec_prices JSONB,
    cos_ratio NUMERIC(5,2),
    cos_fee BIGINT,
    service_ratio NUMERIC(5,2),
    status SMALLINT NOT NULL DEFAULT 1,
    check_status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE TABLE IF NOT EXISTS product_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(50),
    product_id VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    cover VARCHAR(1000),
    price BIGINT,
    price_text VARCHAR(50),
    shop_id BIGINT,
    shop_name VARCHAR(200),
    status INTEGER,
    status_text VARCHAR(50),
    category_name VARCHAR(100),
    product_stock VARCHAR(50),
    sales BIGINT,
    detail_url VARCHAR(1000),
    promotion_start_time VARCHAR(50),
    promotion_end_time VARCHAR(50),
    activity_cos_ratio BIGINT,
    activity_cos_ratio_text VARCHAR(50),
    cos_type INTEGER,
    cos_type_text VARCHAR(50),
    ad_service_ratio VARCHAR(50),
    activity_ad_cos_ratio BIGINT,
    has_douin_goods_tag BOOLEAN,
    raw_payload TEXT,
    sync_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    CONSTRAINT uk_product_snapshot_activity_product UNIQUE (activity_id, product_id)
);

CREATE TABLE IF NOT EXISTS talent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    douyin_uid VARCHAR(50) NOT NULL UNIQUE,
    douyin_no VARCHAR(100),
    uid VARCHAR(100),
    sec_uid VARCHAR(255),
    profile_url TEXT,
    nickname VARCHAR(200),
    fans_count BIGINT DEFAULT 0,
    fans_level VARCHAR(20),
    avatar_url VARCHAR(1000),
    intro TEXT,
    categories JSONB,
    contact_phone VARCHAR(50),
    contact_wechat VARCHAR(100),
    talent_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    tag_updated_by UUID,
    shipping_recipient_name VARCHAR(100),
    shipping_recipient_phone VARCHAR(32),
    shipping_recipient_address VARCHAR(512),
    likes_count BIGINT DEFAULT 0,
    following_count BIGINT DEFAULT 0,
    works_count BIGINT DEFAULT 0,
    ip_location VARCHAR(100),
    crawl_status SMALLINT NOT NULL DEFAULT 0,
    crawl_message VARCHAR(500),
    last_crawl_at TIMESTAMP,
    enrich_status VARCHAR(50),
    last_enrich_time TIMESTAMP,
    data_source VARCHAR(50),
    douyin_account VARCHAR(100),
    talent_uid VARCHAR(100),
    talent_level VARCHAR(50),
    sales_30d BIGINT DEFAULT 0,
    sync_status VARCHAR(50),
    last_sync_time TIMESTAMP,
    sync_error_code VARCHAR(50),
    sync_error_message TEXT,
    raw_payload JSONB,
    unsupported_fields JSONB,
    blacklisted BOOLEAN DEFAULT FALSE,
    blacklist_reason VARCHAR(255),
    status SMALLINT NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE TABLE IF NOT EXISTS talent_claim (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id UUID NOT NULL,
    talent_uid VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    dept_id UUID,
    claim_type SMALLINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    apply_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP,
    expire_time TIMESTAMP,
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(32),
    recipient_address VARCHAR(512),
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    version INTEGER NOT NULL DEFAULT 0,
    remark TEXT
);

CREATE TABLE IF NOT EXISTS crawler_talent_info (
    id BIGSERIAL PRIMARY KEY,
    talent_id VARCHAR(64) NOT NULL UNIQUE,
    nickname VARCHAR(128),
    avatar_url VARCHAR(512),
    fans_count BIGINT DEFAULT 0,
    credit_score DECIMAL(3,2) DEFAULT 0,
    main_category VARCHAR(64),
    region VARCHAR(32),
    last_crawl_time TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS pick_source_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    short_id VARCHAR(10) NOT NULL UNIQUE,
    uuid_seed UUID,
    dept_id UUID,
    pick_source VARCHAR(128) NOT NULL,
    colonel_buyin_id VARCHAR(32),
    product_id VARCHAR(50),
    activity_id VARCHAR(50),
    promotion_link_id UUID,
    channel_user_name VARCHAR(100),
    talent_id VARCHAR(50),
    talent_name VARCHAR(200),
    source_url TEXT,
    converted_url TEXT,
    click_count INT DEFAULT 0,
    order_count INT DEFAULT 0,
    order_amount BIGINT DEFAULT 0,
    pick_extra VARCHAR(128),
    scene VARCHAR(32) DEFAULT 'PRODUCT_LIBRARY',
    source_type VARCHAR(32) DEFAULT 'PICK_SOURCE',
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sample_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_no VARCHAR(50) NOT NULL UNIQUE,
    talent_id UUID NOT NULL,
    talent_uid VARCHAR(50),
    talent_nickname VARCHAR(200),
    talent_fans_count BIGINT,
    talent_credit_score DECIMAL(3,2),
    talent_main_category VARCHAR(64),
    product_id UUID NOT NULL,
    user_id UUID NOT NULL,
    dept_id UUID,
    channel_user_id UUID,
    channel_dept_id UUID,
    expected_sample_num INT DEFAULT 1,
    actual_sample_num INT DEFAULT 0,
    tracking_no VARCHAR(100),
    shipper_code VARCHAR(50),
    logistics_company VARCHAR(50),
    sample_fee BIGINT DEFAULT 0,
    status INTEGER,
    reject_reason VARCHAR(500),
    audit_time TIMESTAMP,
    ship_time TIMESTAMP,
    deliver_time TIMESTAMP,
    complete_time TIMESTAMP,
    close_time TIMESTAMP,
    close_reason VARCHAR(500),
    remark TEXT,
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(32),
    recipient_address VARCHAR(512),
    extra_data JSONB,
    apply_source VARCHAR(50),
    external_apply_id VARCHAR(100),
    external_status VARCHAR(50),
    external_error_code VARCHAR(50),
    external_error_message TEXT,
    external_raw_payload JSONB,
    logistics_status VARCHAR(50),
    logistics_status_name VARCHAR(100),
    logistics_last_query_at TIMESTAMP,
    logistics_last_error TEXT,
    logistics_raw_payload JSONB,
    signed_at TIMESTAMP,
    logistics_provider VARCHAR(100),
    logistics_subscribe_status VARCHAR(50),
    logistics_subscribed_at TIMESTAMP,
    logistics_last_subscribe_at TIMESTAMP,
    logistics_last_callback_at TIMESTAMP,
    logistics_callback_status VARCHAR(50),
    logistics_callback_message TEXT,
    logistics_exception_reason TEXT,
    external_last_sync_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE TABLE IF NOT EXISTS sample_status_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    from_status SMALLINT,
    to_status SMALLINT NOT NULL,
    operator_id UUID,
    operate_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark TEXT
);

CREATE TABLE IF NOT EXISTS colonelsettlement_order (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50),
    product_name VARCHAR(500),
    shop_id BIGINT,
    shop_name VARCHAR(200),
    order_amount BIGINT DEFAULT 0,
    actual_amount BIGINT DEFAULT 0,
    settle_amount BIGINT DEFAULT 0,
    estimate_service_fee BIGINT DEFAULT 0,
    effective_service_fee BIGINT DEFAULT 0,
    estimate_tech_service_fee BIGINT DEFAULT 0,
    effective_tech_service_fee BIGINT DEFAULT 0,
    estimate_service_fee_expense BIGINT DEFAULT 0,
    effective_service_fee_expense BIGINT DEFAULT 0,
    flow_point VARCHAR(64),
    colonel_buyin_id BIGINT,
    colonel_activity_id VARCHAR(50),
    settle_colonel_commission BIGINT DEFAULT 0,
    settle_colonel_tech_service_fee BIGINT DEFAULT 0,
    second_colonel_buyin_id BIGINT,
    second_colonel_activity_id VARCHAR(50),
    settle_second_colonel_commission BIGINT DEFAULT 0,
    phase_id VARCHAR(50),
    order_status SMALLINT,
    order_type SMALLINT,
    settle_time TIMESTAMP,
    cursor VARCHAR(100),
    pick_source VARCHAR(128),
    channel_user_id UUID,
    channel_user_name VARCHAR(100),
    channel_dept_id UUID,
    user_id UUID,
    dept_id UUID,
    colonel_user_id UUID,
    colonel_user_name VARCHAR(100),
    promotion_link_id UUID,
    product_title VARCHAR(500),
    product_pic VARCHAR(512),
    talent_name VARCHAR(200),
    talent_id UUID,
    attribution_status VARCHAR(32) DEFAULT 'UNATTRIBUTED',
    attribution_remark VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    extra_data JSONB,
    CONSTRAINT pk_cso PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

CREATE TABLE IF NOT EXISTS cso_2026_04 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS cso_2026_05 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS cso_2026_06 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS cso_2026_07 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS cso_2026_08 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS cso_2026_09 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS cso_2026_10 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS cso_2026_11 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS cso_2026_12 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS cso_2027_01 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE IF NOT EXISTS cso_2027_02 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE IF NOT EXISTS cso_2027_03 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');

CREATE TABLE IF NOT EXISTS product_operation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    before_status VARCHAR(64),
    after_status VARCHAR(64),
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    operation_payload TEXT,
    operation_remark VARCHAR(500),
    operator_id UUID,
    operator_dept_id UUID,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE INDEX IF NOT EXISTS idx_product_op_log_activity_product
    ON product_operation_log(activity_id, product_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_product_op_log_operator ON product_operation_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_product_op_log_deleted ON product_operation_log(deleted);

CREATE TABLE IF NOT EXISTS product_operation_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(50),
    product_id VARCHAR(50),
    bound_activity_id VARCHAR(50),
    biz_status VARCHAR(64),
    assignee_id UUID,
    audit_status INTEGER,
    audit_remark TEXT,
    audit_payload TEXT,
    promote_link TEXT,
    short_link TEXT,
    promotion_scene INTEGER,
    external_unique_id VARCHAR(255),
    selected_to_library BOOLEAN DEFAULT FALSE,
    selected_at TIMESTAMP,
    selected_by UUID,
    last_operation_at TIMESTAMP,
    pinned_at TIMESTAMP,
    pinned_until TIMESTAMP,
    pinned_by UUID,
    display_status VARCHAR(50),
    first_displayed_at TIMESTAMP,
    last_displayed_at TIMESTAMP,
    hidden_reason VARCHAR(255),
    display_reason VARCHAR(255),
    display_rule_version INTEGER,
    force_display BOOLEAN,
    force_display_by UUID,
    force_display_reason TEXT,
    force_display_until TIMESTAMP,
    display_priority INTEGER,
    manual_disabled BOOLEAN,
    version INTEGER NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    CONSTRAINT uk_pos_activity_product UNIQUE (activity_id, product_id)
);

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

CREATE TABLE IF NOT EXISTS operation_log (
    id UUID NOT NULL,
    user_id UUID,
    username VARCHAR(100),
    module VARCHAR(50),
    action VARCHAR(50),
    target_type VARCHAR(50),
    target_id VARCHAR(50),
    target_name VARCHAR(255),
    content TEXT,
    request_method VARCHAR(10),
    request_url VARCHAR(1000),
    request_params JSONB,
    request_body JSONB,
    response_code VARCHAR(10),
    response_body JSONB,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    duration_ms BIGINT,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_operation_log PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

CREATE TABLE IF NOT EXISTS op_log_2026_04 PARTITION OF operation_log FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS op_log_2026_05 PARTITION OF operation_log FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS op_log_2026_06 PARTITION OF operation_log FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS op_log_2026_07 PARTITION OF operation_log FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS op_log_2026_08 PARTITION OF operation_log FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS op_log_2026_09 PARTITION OF operation_log FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS op_log_2026_10 PARTITION OF operation_log FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS op_log_2026_11 PARTITION OF operation_log FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS op_log_2026_12 PARTITION OF operation_log FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS op_log_2027_01 PARTITION OF operation_log FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE IF NOT EXISTS op_log_2027_02 PARTITION OF operation_log FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE IF NOT EXISTS op_log_2027_03 PARTITION OF operation_log FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');

CREATE TABLE IF NOT EXISTS system_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(20),
    config_group VARCHAR(50),
    config_name VARCHAR(200),
    sort_order INT DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    remark VARCHAR(500),
    config_version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE
);

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
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    CONSTRAINT chk_commissions_dimension_type
        CHECK (dimension_type IN ('global', 'activity', 'product', 'user')),
    CONSTRAINT chk_commissions_commission_type
        CHECK (commission_type IN ('recruiter', 'channel')),
    CONSTRAINT chk_commissions_ratio_range
        CHECK (ratio >= 0 AND ratio <= 1)
);

CREATE INDEX IF NOT EXISTS idx_commissions_dimension_lookup
    ON commissions (dimension_type, dimension_id, commission_type, status, deleted);

-- Compatibility patches for containers created with older init scripts (safe to re-run)
ALTER TABLE sample_request ADD COLUMN IF NOT EXISTS shipper_code VARCHAR(32);
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS estimate_service_fee_expense BIGINT DEFAULT 0;
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS effective_service_fee_expense BIGINT DEFAULT 0;
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS product_pic VARCHAR(512);

CREATE TABLE IF NOT EXISTS merchant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     VARCHAR(50)  NOT NULL UNIQUE,         -- 抖店商家ID
    merchant_name   VARCHAR(200),
    shop_id         BIGINT,                                 -- 关联店铺ID
    shop_name       VARCHAR(200),
    source_order_id VARCHAR(50),                           -- 首次来源订单ID
    owner_id        UUID,                                  -- owner user id
    owner_dept_id   UUID,                                  -- owner dept id
    status          SMALLINT  NOT NULL DEFAULT 1,          -- 1=启用, 0=禁用
    deleted         SMALLINT  NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    version         INTEGER NOT NULL DEFAULT 0,
    extra_data      JSONB
);
CREATE INDEX IF NOT EXISTS idx_merchant_merchant_id ON merchant(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_shop_id     ON merchant(shop_id);
CREATE INDEX IF NOT EXISTS idx_merchant_owner_id    ON merchant(owner_id);
CREATE INDEX IF NOT EXISTS idx_merchant_owner_dept  ON merchant(owner_dept_id);
CREATE INDEX IF NOT EXISTS idx_merchant_deleted     ON merchant(deleted);

CREATE TABLE IF NOT EXISTS exclusive_merchant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         VARCHAR(50) NOT NULL,
    merchant_name       VARCHAR(200),
    shop_id             BIGINT,
    user_id             UUID NOT NULL,
    dept_id             UUID NOT NULL,
    effective_month     VARCHAR(7) NOT NULL,
    service_fee         BIGINT   NOT NULL,
    business_total_fee  BIGINT   NOT NULL,
    service_fee_ratio   NUMERIC(5,2) NOT NULL,
    start_date          DATE     NOT NULL,
    end_date            DATE,
    status              SMALLINT NOT NULL DEFAULT 1,
    deleted             SMALLINT  NOT NULL DEFAULT 0,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    remark              TEXT,
    CONSTRAINT ck_exclusive_merchant_non_negative_financials CHECK (
        service_fee >= 0
        AND business_total_fee >= 0
        AND service_fee_ratio >= 0
    ),
    CONSTRAINT ck_exclusive_merchant_status CHECK (status IN (0, 1, 2))
);
CREATE INDEX IF NOT EXISTS idx_em_merchant_id    ON exclusive_merchant(merchant_id);
CREATE INDEX IF NOT EXISTS idx_em_shop_id        ON exclusive_merchant(shop_id);
CREATE INDEX IF NOT EXISTS idx_em_user_id       ON exclusive_merchant(user_id);
CREATE INDEX IF NOT EXISTS idx_em_dept_id       ON exclusive_merchant(dept_id);
CREATE INDEX IF NOT EXISTS idx_em_effective_mon  ON exclusive_merchant(effective_month);
CREATE INDEX IF NOT EXISTS idx_em_status         ON exclusive_merchant(status);
CREATE INDEX IF NOT EXISTS idx_em_deleted       ON exclusive_merchant(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_em_merchant_month ON exclusive_merchant(merchant_id, effective_month);

ALTER TABLE exclusive_merchant
    ADD CONSTRAINT fk_em_merchant
    FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS system_config (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key    VARCHAR(100) NOT NULL UNIQUE,
    config_value  TEXT,
    config_type   VARCHAR(20),
    config_group  VARCHAR(50),
    config_name   VARCHAR(200),
    sort_order    INT       DEFAULT 0,
    status        SMALLINT  NOT NULL DEFAULT 1,
    deleted       SMALLINT  NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    remark        VARCHAR(500),
    config_version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_sc_config_key    ON system_config(config_key);
CREATE INDEX IF NOT EXISTS idx_sc_config_group  ON system_config(config_group);
CREATE INDEX IF NOT EXISTS idx_sc_status        ON system_config(status);
CREATE INDEX IF NOT EXISTS idx_sc_deleted       ON system_config(deleted);

CREATE TABLE IF NOT EXISTS system_config_change_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id     UUID,
    config_key    VARCHAR(100) NOT NULL,
    change_action VARCHAR(20)  NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    source        VARCHAR(50)  NOT NULL,
    operator_id   UUID,
    changed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_id      UUID,
    change_reason TEXT,
    config_version INT
);
CREATE INDEX IF NOT EXISTS idx_sccl_config_key_changed
    ON system_config_change_log(config_key, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_sccl_operator_changed
    ON system_config_change_log(operator_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS domain_event_outbox (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key       VARCHAR(255),
    event_type      VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128),
    event_version   INT          NOT NULL DEFAULT 1,
    payload         JSONB        NOT NULL,
    headers         JSONB,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retry       INTEGER      NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP,
    error_message   TEXT,
    occurred_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    created_by      VARCHAR(64),
    trace_id        VARCHAR(128)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_event_outbox_event_key
    ON domain_event_outbox (event_key)
    WHERE event_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_status_time
    ON domain_event_outbox (status, occurred_at);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_type_time
    ON domain_event_outbox (event_type, occurred_at);

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
