CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS price BIGINT,
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS activity_id UUID;

UPDATE product
SET price = COALESCE(price, discount_price, market_price)
WHERE price IS NULL;

ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS douyin_no VARCHAR(50),
    ADD COLUMN IF NOT EXISTS uid VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sec_uid VARCHAR(100),
    ADD COLUMN IF NOT EXISTS profile_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS enrich_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_enrich_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(32);

UPDATE talent
SET uid = COALESCE(uid, douyin_uid),
    data_source = COALESCE(data_source, 'local-mock')
WHERE uid IS NULL
   OR data_source IS NULL;

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS promotion_link_id UUID,
    ADD COLUMN IF NOT EXISTS channel_user_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS talent_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS talent_name VARCHAR(200);

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_user_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS colonel_user_id UUID,
    ADD COLUMN IF NOT EXISTS colonel_user_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS promotion_link_id UUID,
    ADD COLUMN IF NOT EXISTS product_title VARCHAR(500),
    ADD COLUMN IF NOT EXISTS talent_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS attribution_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS attribution_remark VARCHAR(500);

ALTER TABLE colonelsettlement_order
    ALTER COLUMN pick_source TYPE VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_cso_attribution_status ON colonelsettlement_order (attribution_status);

CREATE TABLE IF NOT EXISTS product_snapshot (
    id UUID PRIMARY KEY,
    activity_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    title VARCHAR(500),
    cover VARCHAR(1000),
    price BIGINT,
    price_text VARCHAR(64),
    shop_id BIGINT,
    shop_name VARCHAR(255),
    status INTEGER,
    status_text VARCHAR(64),
    category_name VARCHAR(255),
    product_stock VARCHAR(64),
    sales BIGINT,
    detail_url VARCHAR(1000),
    promotion_start_time VARCHAR(64),
    promotion_end_time VARCHAR(64),
    activity_cos_ratio BIGINT,
    activity_cos_ratio_text VARCHAR(64),
    cos_type INTEGER,
    cos_type_text VARCHAR(64),
    ad_service_ratio VARCHAR(32),
    activity_ad_cos_ratio BIGINT,
    has_douin_goods_tag BOOLEAN,
    raw_payload TEXT,
    sync_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_snapshot_activity_product
    ON product_snapshot(activity_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_activity
    ON product_snapshot(activity_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_product
    ON product_snapshot(product_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_deleted
    ON product_snapshot(deleted);

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
CREATE INDEX IF NOT EXISTS idx_product_op_log_operator
    ON product_operation_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_product_op_log_deleted
    ON product_operation_log(deleted);

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
    doukouling VARCHAR(200),
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

CREATE INDEX IF NOT EXISTS idx_pl_product_id
    ON promotion_link(product_id);
CREATE INDEX IF NOT EXISTS idx_pl_pick_source
    ON promotion_link(pick_source);
CREATE INDEX IF NOT EXISTS idx_pl_channel_user
    ON promotion_link(channel_user_id);

CREATE TABLE IF NOT EXISTS talent_follow_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(64) NOT NULL,
    talent_id UUID,
    talent_name VARCHAR(200),
    follow_status VARCHAR(64) NOT NULL,
    content TEXT,
    next_follow_time TIMESTAMP,
    operator_id UUID,
    operator_name VARCHAR(100),
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE INDEX IF NOT EXISTS idx_talent_follow_record_activity_product
    ON talent_follow_record(activity_id, product_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_talent_follow_record_talent_id
    ON talent_follow_record(talent_id);

CREATE TABLE IF NOT EXISTS product_operation_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    bound_activity_id VARCHAR(64),
    biz_status VARCHAR(64),
    assignee_id UUID,
    audit_status INTEGER DEFAULT 0,
    audit_remark TEXT,
    promote_link TEXT,
    short_link TEXT,
    promotion_scene INTEGER,
    external_unique_id VARCHAR(128),
    last_operation_at TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_op_state_activity_product
    ON product_operation_state(activity_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_op_state_assignee
    ON product_operation_state(assignee_id);
CREATE TABLE IF NOT EXISTS crawler_talent_info (
    id BIGSERIAL PRIMARY KEY,
    talent_id VARCHAR(64) NOT NULL UNIQUE,
    nickname VARCHAR(128),
    avatar_url VARCHAR(512),
    fans_count BIGINT DEFAULT 0,
    credit_score DECIMAL(3, 2),
    main_category VARCHAR(100),
    region VARCHAR(100),
    crawl_status INTEGER DEFAULT 0,
    last_crawl_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE crawler_talent_info
    ADD COLUMN IF NOT EXISTS id BIGSERIAL,
    ADD COLUMN IF NOT EXISTS crawl_status INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_crawl_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_cti_fans ON crawler_talent_info(fans_count DESC);
CREATE INDEX IF NOT EXISTS idx_cti_region ON crawler_talent_info(region);
