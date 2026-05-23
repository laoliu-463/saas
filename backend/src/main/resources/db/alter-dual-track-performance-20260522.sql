-- 订单域 V1.6 双轨金额 + 业绩域 V1.1 performance_records

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS settle_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimate_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimate_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_tech_service_fee BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN colonelsettlement_order.settle_amount IS '结算订单额（分），对应 pay_amount 的结算轨';
COMMENT ON COLUMN colonelsettlement_order.estimate_service_fee IS '预估服务费收入（分）';
COMMENT ON COLUMN colonelsettlement_order.effective_service_fee IS '有效/结算服务费收入（分）';
COMMENT ON COLUMN colonelsettlement_order.estimate_tech_service_fee IS '预估技术服务费（分）';
COMMENT ON COLUMN colonelsettlement_order.effective_tech_service_fee IS '结算技术服务费（分）';

-- 历史数据回填：order_amount 即 pay_amount；actual_amount 近似 settle_amount
UPDATE colonelsettlement_order
SET settle_amount = COALESCE(NULLIF(actual_amount, 0), order_amount, 0),
    estimate_service_fee = COALESCE(NULLIF(settle_colonel_commission, 0), 0),
    effective_service_fee = COALESCE(settle_colonel_commission, 0),
    estimate_tech_service_fee = COALESCE(settle_colonel_tech_service_fee, 0),
    effective_tech_service_fee = COALESCE(settle_colonel_tech_service_fee, 0)
WHERE estimate_service_fee = 0
  AND effective_service_fee = 0;

CREATE TABLE IF NOT EXISTS performance_records (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                        VARCHAR(50) NOT NULL,
    order_row_id                    UUID,
    default_channel_user_id         UUID,
    default_recruiter_user_id       UUID,
    final_channel_user_id           UUID,
    final_recruiter_user_id         UUID,
    channel_attribution             VARCHAR(32),
    recruiter_attribution           VARCHAR(32),
    talent_id                       UUID,
    partner_id                      BIGINT,
    product_id                      VARCHAR(50),
    activity_id                     VARCHAR(50),
    pay_amount                      BIGINT NOT NULL DEFAULT 0,
    settle_amount                   BIGINT NOT NULL DEFAULT 0,
    estimate_service_fee            BIGINT NOT NULL DEFAULT 0,
    effective_service_fee           BIGINT NOT NULL DEFAULT 0,
    estimate_tech_service_fee       BIGINT NOT NULL DEFAULT 0,
    effective_tech_service_fee      BIGINT NOT NULL DEFAULT 0,
    estimate_service_profit         BIGINT NOT NULL DEFAULT 0,
    effective_service_profit        BIGINT NOT NULL DEFAULT 0,
    estimate_recruiter_commission   BIGINT NOT NULL DEFAULT 0,
    effective_recruiter_commission  BIGINT NOT NULL DEFAULT 0,
    estimate_channel_commission     BIGINT NOT NULL DEFAULT 0,
    effective_channel_commission    BIGINT NOT NULL DEFAULT 0,
    estimate_gross_profit           BIGINT NOT NULL DEFAULT 0,
    effective_gross_profit          BIGINT NOT NULL DEFAULT 0,
    recruiter_commission_rate       NUMERIC(8, 4),
    channel_commission_rate         NUMERIC(8, 4),
    order_status                    SMALLINT,
    settle_time                     TIMESTAMP,
    order_create_time               TIMESTAMP,
    is_valid                        BOOLEAN NOT NULL DEFAULT TRUE,
    is_reversed                     BOOLEAN NOT NULL DEFAULT FALSE,
    calculation_version             INTEGER NOT NULL DEFAULT 1,
    calculated_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_performance_records_order_id UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_performance_records_settle_time
    ON performance_records (settle_time DESC);

CREATE INDEX IF NOT EXISTS idx_performance_records_final_channel
    ON performance_records (final_channel_user_id, settle_time DESC)
    WHERE is_valid = TRUE;

CREATE INDEX IF NOT EXISTS idx_performance_records_final_recruiter
    ON performance_records (final_recruiter_user_id, settle_time DESC)
    WHERE is_valid = TRUE;

COMMENT ON TABLE performance_records IS '业绩记录（双轨提成，业绩域 V1.1）';
