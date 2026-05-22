-- =============================================
-- DB contract and performance guardrails
-- =============================================
-- Schema-only migration. Existing data backfill/cleanup stays out of this file.

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS colonel_buyin_id BIGINT;
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS second_colonel_buyin_id BIGINT;
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS second_colonel_activity_id VARCHAR(50);
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS phase_id VARCHAR(50);
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS order_type SMALLINT;
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS cursor VARCHAR(100);

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

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS biz_status VARCHAR(64);
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
