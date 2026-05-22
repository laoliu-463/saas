-- =============================================
-- 抖音团长 SaaS 系统 - 数据库迁移合并脚本
-- 基于 alter-*.sql 合并，执行顺序按文件名推断
-- 合并时间：2026-05-21
-- 依赖：init-db.sql 先执行
-- 幂等设计：所有操作均使用 IF NOT EXISTS / IF NOT EXISTS
-- =============================================

-- ==== alter-test-existing-volumes-20260504.sql ====
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN DEFAULT FALSE;

-- ==== alter-merchant-ownership.sql ====
-- 商家归属人支持：增加 owner_id / owner_dept_id 列，并统一为 UUID 口径
ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS owner_id UUID,
    ADD COLUMN IF NOT EXISTS owner_dept_id UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_id TYPE UUID
    USING NULLIF(TRIM(owner_id::text), '')::UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_dept_id TYPE UUID
    USING NULLIF(TRIM(owner_dept_id::text), '')::UUID;

-- ==== alter-product-test-columns.sql ====
-- 商品表补充字段（原 test 专用 schema 独有，现统一到主链路）
-- 执行前置：init-db.sql
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS external_product_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS test_tag VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_product_external_id
    ON product(external_product_id)
    WHERE external_product_id IS NOT NULL;

-- ==== alter-product-extended-columns.sql ====
-- 商品表补充字段（原 test 专用 schema 独有，现统一到主链路）
-- 执行前置：init-db.sql
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS quality_tag VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS creator_ip VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version_tag INTEGER DEFAULT 0;

-- ==== alter-product-biz-status.sql ====
ALTER TABLE product_operation_state
ADD COLUMN IF NOT EXISTS biz_status VARCHAR(64);

-- ==== alter-product-following.sql ====
CREATE TABLE IF NOT EXISTS talent_follow_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       VARCHAR(64) NOT NULL,
    talent_id        UUID NOT NULL,
    user_id          UUID,
    follow_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    unfollow_time    TIMESTAMP,
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tfr_product_id ON talent_follow_record(product_id);
CREATE INDEX IF NOT EXISTS idx_tfr_talent_id  ON talent_follow_record(talent_id);
CREATE INDEX IF NOT EXISTS idx_tfr_user_id    ON talent_follow_record(user_id);

-- ==== alter-product-main-chain.sql ====
-- 商品库主链路改造（P0）
-- 执行前置：init-db.sql
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS assignee_id   UUID,
    ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS audit_status VARCHAR(32) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS audit_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_pos_assignee ON product_operation_state(assignee_id)
    WHERE assignee_id IS NOT NULL;

-- ==== alter-sys-dept.sql ====
-- =============================================
-- sys_dept 业务组表补齐（棕地增量）
-- =============================================
ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS sort_order  INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dept_type   VARCHAR(32) DEFAULT 'BUSINESS',
    ADD COLUMN IF NOT EXISTS outer_dept_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sys_dept_type ON sys_dept(dept_type);

-- ==== alter-menu-permission-model.sql ====
-- ============================================================
-- Gap 1: 菜单/操作权限模型 — sys_menu + sys_role_menu
-- ============================================================
ALTER TABLE sys_menu
    ADD COLUMN IF NOT EXISTS menu_type    VARCHAR(16) DEFAULT 'MENU',
    ADD COLUMN IF NOT EXISTS permission   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_frame     BOOLEAN    DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache        BOOLEAN    DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS visible       BOOLEAN    DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS status        VARCHAR(16) DEFAULT 'ENABLED',
    ADD COLUMN IF NOT EXISTS perms        VARCHAR(100);

ALTER TABLE sys_role_menu
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ==== alter-sample-shipper-code.sql ====
ALTER TABLE sample_request
ADD COLUMN IF NOT EXISTS shipper_code VARCHAR(32);

-- ==== alter-talent-profile-sync.sql ====
-- =============================================
-- 增量脚本：达人真实资料同步字段与同步日志
-- 适用：已存在 colonel_saas 数据库的环境
-- =============================================
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS follower_count   INTEGER,
    ADD COLUMN IF NOT EXISTS avg_views        NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS last_synced_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sync_error       VARCHAR(512);

ALTER TABLE talent_profile_sync_log
    ADD COLUMN IF NOT EXISTS request_params TEXT,
    ADD COLUMN IF NOT EXISTS response_code VARCHAR(32);

-- ==== alter-talent-enrich.sql ====
-- =============================================
-- 增量脚本：达人自动补全与字段来源审计
-- 适用：已存在 colonel_saas 数据库的环境，手工执行
-- =============================================
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS birthday          DATE,
    ADD COLUMN IF NOT EXISTS cert_no           VARCHAR(64),
    ADD COLUMN IF NOT EXISTS contact_name      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS contact_phone     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS live_room_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS live_fans_count   INTEGER,
    ADD COLUMN IF NOT EXISTS last_live_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS categories        TEXT[],
    ADD COLUMN IF NOT EXISTS tags              TEXT[],
    ADD COLUMN IF NOT EXISTS en_name           VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_platform   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_url        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS fail_reason       VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cert_image_url    VARCHAR(512),
    ADD COLUMN IF NOT EXISTS avatar_image_url  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS data_source      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS external_id      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS data_version     INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS verified_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verified_by      UUID,
    ADD COLUMN IF NOT EXISTS source_fields    TEXT,
    ADD COLUMN IF NOT EXISTS latest_synced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_talent_cert_no       ON talent(cert_no)      WHERE cert_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_talent_contact_phone ON talent(contact_phone) WHERE contact_phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_talent_external_id   ON talent(external_id)  WHERE external_id IS NOT NULL;

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS claim_channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS expire_time   TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_tc_claim_channel ON talent_claim(claim_channel)
    WHERE claim_channel IS NOT NULL;

ALTER TABLE talent_protect
    ADD COLUMN IF NOT EXISTS protect_level VARCHAR(16) DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS expire_time   TIMESTAMP;

-- ==== alter-talent-crm-gap-fill.sql ====
-- =============================================
-- 增量脚本：达人 CRM 表结构差异补齐
-- 执行前置：init-db.sql、alter-talent-enrich.sql
-- =============================================
ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS current_cycle_start DATE,
    ADD COLUMN IF NOT EXISTS current_cycle_end   DATE,
    ADD COLUMN IF NOT EXISTS last_active_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS total_order_count   INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_gmv          NUMERIC(14,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS claim_source       VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tc_user_talent ON talent_claim(user_id, talent_id)
    WHERE status = 'ACTIVE';

-- ==== alter-order-talent-id.sql ====
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS talent_id UUID;

-- ==== alter-order-pick-source-length.sql ====
ALTER TABLE colonelsettlement_order
    ALTER COLUMN pick_source TYPE VARCHAR(128);

-- ==== alter-order-attribution-mvp.sql ====
ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_status VARCHAR(32) DEFAULT 'UNATTRIBUTED';

ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_remark VARCHAR(255);

-- ==== alter-order-attribution-mvp-v2.sql ====
-- =============================================
-- 订单回流与归因 MVP 增强脚本
-- =============================================
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS attribution_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_attempt_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS attempt_count     INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attribution_amt   NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS service_fee_amt    NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS settle_status     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS settle_time        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS colonelsettlement_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS colonel_nickname  VARCHAR(128),
    ADD COLUMN IF NOT EXISTS product_title     VARCHAR(256),
    ADD COLUMN IF NOT EXISTS product_pic       VARCHAR(512),
    ADD COLUMN IF NOT EXISTS order_source     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS activity_title   VARCHAR(256),
    ADD COLUMN IF NOT EXISTS order_type       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS origin           VARCHAR(64),
    ADD COLUMN IF NOT EXISTS attribution_rule VARCHAR(64),
    ADD COLUMN IF NOT EXISTS colonelsettlement_time TIMESTAMP;

ALTER TABLE colonelsettlement_order
    ALTER COLUMN attribution_status SET DEFAULT 'UNATTRIBUTED',
    ALTER COLUMN settle_status      SET DEFAULT 'UNPAID';

CREATE INDEX IF NOT EXISTS idx_cso_attribution_status ON colonelsettlement_order(attribution_status);
CREATE INDEX IF NOT EXISTS idx_cso_settle_status      ON colonelsettlement_order(settle_status);
CREATE INDEX IF NOT EXISTS idx_cso_order_type         ON colonelsettlement_order(order_type);

-- ==== alter-pick-source-mapping-colonel-buyin-id.sql ====
-- 抖店原生订单回流会返回 19 位 colonel_buyin_id，不能复用 8-10 位 short_id。
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS colonel_buyin_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_psm_colonel_id ON pick_source_mapping(colonel_buyin_id)
    WHERE colonel_buyin_id IS NOT NULL;

-- ==== alter-pick-source-mapping-duplicate-pick-source.sql ====
-- 抖店真实转链场景下，同一个 pick_source 可能被上游复用到多个活动商品。
-- 不能再把 pick_source 作为全局唯一键，否则后一次转链会覆盖前一次映射。
ALTER TABLE pick_source_mapping
    DROP CONSTRAINT IF EXISTS uk_pick_source;

ALTER TABLE pick_source_mapping
    ADD CONSTRAINT uk_pick_source_product
        UNIQUE (pick_source, product_id);

-- ==== alter-pick-source-mapping-native-source-type.sql ====
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'PICK_SOURCE';

ALTER TABLE pick_source_mapping
    ALTER COLUMN source_type SET DEFAULT 'PICK_SOURCE';

CREATE INDEX IF NOT EXISTS idx_psm_source_type ON pick_source_mapping(source_type);

-- ==== alter-pick-source-mapping-pick-extra-length.sql ====
ALTER TABLE pick_source_mapping
    ALTER COLUMN pick_extra TYPE VARCHAR(128);

-- ==== alter-partitioned-order-log-pk-default.sql ====
-- =============================================
-- 分区表：订单 / 操作日志 — id 默认值与主键（棕地一次执行）
-- =============================================
ALTER TABLE operation_log
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE colonelsettlement_order
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

CREATE OR REPLACE FUNCTION ensure_partition_pk()
RETURNS void AS $$
DECLARE
    partition_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO partition_count
    FROM pg_tables
    WHERE tablename LIKE 'colonelsettlement_order_%';
END;
$$ LANGUAGE plpgsql;

SELECT ensure_partition_pk();
DROP FUNCTION IF EXISTS ensure_partition_pk();

-- ==== alter-cso-sample-attribution-indexes.sql ====
-- =============================================
-- M6/M7/M8 归因与独家达人统计 — 索引补齐
-- =============================================
CREATE INDEX IF NOT EXISTS idx_cso_user_create
    ON colonelsettlement_order(user_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_cso_dept_create
    ON colonelsettlement_order(dept_id, create_time DESC)
    WHERE dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_colonel_id
    ON colonelsettlement_order(colonel_buyin_id)
    WHERE colonel_buyin_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_talent_id
    ON colonelsettlement_order(talent_id)
    WHERE talent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_pick_source
    ON colonelsettlement_order(pick_source)
    WHERE pick_source IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_attribution
    ON colonelsettlement_order(attribution_status, create_time DESC)
    WHERE attribution_status = 'ATTRIBUTED';

-- ==== alter-db-performance-contract-20260521.sql ====
-- Schema-only guardrails (FIN-01 / FK-01 / STATUS-01). Backfill/cleanup stays in separate scripts.

CREATE INDEX IF NOT EXISTS idx_cso_deleted
    ON colonelsettlement_order (deleted);
CREATE INDEX IF NOT EXISTS idx_cso_user_create_time
    ON colonelsettlement_order (user_id, create_time DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_cso_dept_create_time
    ON colonelsettlement_order (dept_id, create_time DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_sr_channel_user_status
    ON sample_request (channel_user_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pos_product_biz_status
    ON product_operation_state (product_id, biz_status)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pos_product_audit_status
    ON product_operation_state (product_id, audit_status)
    WHERE deleted = 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_data_scope'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_data_scope
            CHECK (data_scope IN (1, 2, 3)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_status'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_status
            CHECK (status IN (0, 1)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sample_request_status'
    ) THEN
        ALTER TABLE sample_request
            ADD CONSTRAINT ck_sample_request_status
            CHECK (status BETWEEN 1 AND 8) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cso_order_type'
    ) THEN
        ALTER TABLE colonelsettlement_order
            ADD CONSTRAINT ck_cso_order_type
            CHECK (order_type IS NULL OR order_type >= 0) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_talent_non_negative_financials'
    ) THEN
        ALTER TABLE exclusive_talent
            ADD CONSTRAINT ck_exclusive_talent_non_negative_financials
            CHECK (
                service_fee >= 0
                AND channel_total_fee >= 0
                AND service_fee_ratio >= 0
                AND monthly_samples >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_merchant_non_negative_financials'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT ck_exclusive_merchant_non_negative_financials
            CHECK (
                service_fee >= 0
                AND business_total_fee >= 0
                AND service_fee_ratio >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_commission_settlement_non_negative_financials'
    ) THEN
        ALTER TABLE commission_settlement
            ADD CONSTRAINT ck_commission_settlement_non_negative_financials
            CHECK (
                order_count >= 0
                AND total_order_amount >= 0
                AND commission_amount >= 0
                AND tech_service_fee >= 0
                AND net_commission >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cso_non_negative_financials'
    ) THEN
        ALTER TABLE colonelsettlement_order
            ADD CONSTRAINT ck_cso_non_negative_financials
            CHECK (
                (order_amount IS NULL OR order_amount >= 0)
                AND (actual_amount IS NULL OR actual_amount >= 0)
                AND (settle_colonel_commission IS NULL OR settle_colonel_commission >= 0)
                AND (settle_colonel_tech_service_fee IS NULL OR settle_colonel_tech_service_fee >= 0)
                AND (settle_second_colonel_commission IS NULL OR settle_second_colonel_commission >= 0)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_talent_status'
    ) THEN
        ALTER TABLE exclusive_talent
            ADD CONSTRAINT ck_exclusive_talent_status
            CHECK (status IN (0, 1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_merchant_status'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT ck_exclusive_merchant_status
            CHECK (status IN (0, 1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_em_merchant'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT fk_em_merchant
            FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_product_operation_state_biz_status'
    ) THEN
        ALTER TABLE product_operation_state
            ADD CONSTRAINT ck_product_operation_state_biz_status
            CHECK (
                biz_status IS NULL
                OR biz_status IN (
                    'PENDING_AUDIT',
                    'APPROVED',
                    'REJECTED',
                    'BOUND',
                    'ASSIGNED',
                    'LINKED',
                    'FOLLOWING',
                    'SYNCED'
                )
            ) NOT VALID;
    END IF;
END $$;

-- ==== alter-optimistic-lock-core-20260522.sql ====
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN talent.version IS '乐观锁版本号';
COMMENT ON COLUMN product_operation_state.version IS '乐观锁版本号';
COMMENT ON COLUMN pick_source_mapping.version IS '乐观锁版本号';
COMMENT ON COLUMN talent_claim.version IS '乐观锁版本号';
COMMENT ON COLUMN sample_request.version IS '乐观锁版本号';
COMMENT ON COLUMN merchant.version IS '乐观锁版本号';
COMMENT ON COLUMN colonelsettlement_order.version IS '乐观锁版本号';

COMMENT ON TABLE colonelsettlement_order IS '团长结算订单表（分区表 by create_time）';
COMMENT ON COLUMN colonelsettlement_order.order_type     IS 'MAIN=主订单接口录入，SETTLEMENT=结算订单接口录入';
COMMENT ON COLUMN colonelsettlement_order.colonel_buyin_id IS '抖店19位团长身份码，用于原生归因';
COMMENT ON COLUMN colonelsettlement_order.pick_source     IS '转链归因短码，优先级低于 colonel_buyin_id';
