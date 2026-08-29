-- 业绩域保存最终归属决策；订单域只保存默认归属输入。
ALTER TABLE performance_records
    ADD COLUMN IF NOT EXISTS default_channel_dept_id UUID,
    ADD COLUMN IF NOT EXISTS default_recruiter_dept_id UUID,
    ADD COLUMN IF NOT EXISTS default_channel_attribution VARCHAR(64),
    ADD COLUMN IF NOT EXISTS default_recruiter_attribution VARCHAR(64),
    ADD COLUMN IF NOT EXISTS final_channel_dept_id UUID,
    ADD COLUMN IF NOT EXISTS final_recruiter_dept_id UUID,
    ADD COLUMN IF NOT EXISTS attribution_rule_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS attribution_decision_snapshot JSONB;

ALTER TABLE performance_records
    ADD COLUMN IF NOT EXISTS talent_commission BIGINT NOT NULL DEFAULT 0;

ALTER TABLE promotion_link
    ADD COLUMN IF NOT EXISTS attribution_snapshot JSONB;

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS attribution_snapshot JSONB;

UPDATE performance_records
SET default_channel_attribution = COALESCE(default_channel_attribution, channel_attribution),
    default_recruiter_attribution = COALESCE(default_recruiter_attribution, recruiter_attribution),
    attribution_rule_version = COALESCE(attribution_rule_version, 'LEGACY_ORDER_FINAL_FACT')
WHERE default_channel_attribution IS NULL
   OR default_recruiter_attribution IS NULL
   OR attribution_rule_version IS NULL;

CREATE INDEX IF NOT EXISTS idx_performance_records_final_channel_dept
    ON performance_records (final_channel_dept_id, order_create_time DESC);
CREATE INDEX IF NOT EXISTS idx_performance_records_final_recruiter_dept
    ON performance_records (final_recruiter_dept_id, order_create_time DESC);

CREATE TABLE IF NOT EXISTS performance_attribution_adjustment (
    id UUID PRIMARY KEY,
    order_id VARCHAR(128) NOT NULL,
    channel_user_id UUID,
    recruiter_user_id UUID,
    channel_dept_id UUID,
    recruiter_dept_id UUID,
    effective_from TIMESTAMP,
    effective_until TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_performance_attribution_adjustment_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED')),
    CONSTRAINT chk_performance_attribution_adjustment_effective_window
        CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_performance_attribution_adjustment_effective
    ON performance_attribution_adjustment (order_id, status, effective_from, effective_until, approved_at DESC);

CREATE TABLE IF NOT EXISTS performance_calculation_execution (
    id UUID PRIMARY KEY,
    event_key VARCHAR(256) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    order_id VARCHAR(128) NOT NULL,
    order_version INTEGER NOT NULL DEFAULT 0,
    event_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_retry_at TIMESTAMP,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_performance_calculation_execution_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_performance_calculation_execution_retry
    ON performance_calculation_execution (status, next_retry_at)
    WHERE status = 'FAILED';

CREATE TABLE IF NOT EXISTS performance_adjustment_ledger (
    id UUID PRIMARY KEY,
    event_key VARCHAR(256) NOT NULL UNIQUE,
    order_id VARCHAR(128) NOT NULL,
    refund_id VARCHAR(128),
    adjustment_type VARCHAR(16) NOT NULL,
    refund_amount BIGINT NOT NULL DEFAULT 0,
    delta_pay_amount BIGINT NOT NULL DEFAULT 0,
    delta_settle_amount BIGINT NOT NULL DEFAULT 0,
    delta_estimate_service_fee BIGINT NOT NULL DEFAULT 0,
    delta_effective_service_fee BIGINT NOT NULL DEFAULT 0,
    delta_estimate_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    delta_effective_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    delta_estimate_service_fee_expense BIGINT NOT NULL DEFAULT 0,
    delta_effective_service_fee_expense BIGINT NOT NULL DEFAULT 0,
    delta_talent_commission BIGINT NOT NULL DEFAULT 0,
    delta_estimate_service_profit BIGINT NOT NULL DEFAULT 0,
    delta_effective_service_profit BIGINT NOT NULL DEFAULT 0,
    delta_estimate_recruiter_commission BIGINT NOT NULL DEFAULT 0,
    delta_effective_recruiter_commission BIGINT NOT NULL DEFAULT 0,
    delta_estimate_channel_commission BIGINT NOT NULL DEFAULT 0,
    delta_effective_channel_commission BIGINT NOT NULL DEFAULT 0,
    delta_estimate_gross_profit BIGINT NOT NULL DEFAULT 0,
    delta_effective_gross_profit BIGINT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL,
    input_snapshot JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_performance_adjustment_ledger_type
        CHECK (adjustment_type IN ('REFUND', 'REVERSAL'))
);

CREATE INDEX IF NOT EXISTS idx_performance_adjustment_ledger_order
    ON performance_adjustment_ledger (order_id, occurred_at);
