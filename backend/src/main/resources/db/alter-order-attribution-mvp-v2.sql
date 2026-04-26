-- =============================================
-- 订单回流与归因 MVP 增强脚本
-- =============================================

-- 1. 创建推广链接表
CREATE TABLE IF NOT EXISTS promotion_link (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        VARCHAR(50)  NOT NULL,
    activity_id       VARCHAR(50),
    talent_id         VARCHAR(50),
    talent_name       VARCHAR(200),
    channel_user_id   UUID,
    channel_user_name VARCHAR(100),
    
    original_product_url TEXT,
    promotion_url     TEXT,
    short_url         TEXT,
    doukouling        VARCHAR(200),
    pick_source       VARCHAR(128),
    pick_extra        VARCHAR(128),
    
    link_status       VARCHAR(20) DEFAULT 'ACTIVE',
    expire_time       TIMESTAMP,
    raw_response      JSONB,
    
    operator_id       UUID,
    operator_name     VARCHAR(100),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pl_product_id ON promotion_link(product_id);
CREATE INDEX IF NOT EXISTS idx_pl_pick_source ON promotion_link(pick_source);
CREATE INDEX IF NOT EXISTS idx_pl_channel_user ON promotion_link(channel_user_id);

-- 2. 增强归因映射表
ALTER TABLE pick_source_mapping ADD COLUMN IF NOT EXISTS promotion_link_id UUID;
ALTER TABLE pick_source_mapping ADD COLUMN IF NOT EXISTS channel_user_name VARCHAR(100);
ALTER TABLE pick_source_mapping ADD COLUMN IF NOT EXISTS talent_id VARCHAR(50);
ALTER TABLE pick_source_mapping ADD COLUMN IF NOT EXISTS talent_name VARCHAR(200);
ALTER TABLE pick_source_mapping ADD COLUMN IF NOT EXISTS scene VARCHAR(32) DEFAULT 'PRODUCT_LIBRARY';
CREATE INDEX IF NOT EXISTS idx_psm_scene ON pick_source_mapping(scene);

-- 3. 订单表增加负责人字段（如果还没的话）
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS colonel_user_id UUID;
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS colonel_user_name VARCHAR(100);
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS channel_user_name VARCHAR(100);
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS promotion_link_id UUID;
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS product_title VARCHAR(500);
ALTER TABLE colonelsettlement_order ADD COLUMN IF NOT EXISTS talent_name VARCHAR(200);
