-- 订单表双轨金额事实 + 业绩计算结果表（O-09 / Y-02）
-- 金额单位与现网一致：BIGINT minor unit（前端 ÷100 展示为元）

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS estimate_tech_service_fee BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_tech_service_fee BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flow_point VARCHAR(64);

CREATE TABLE IF NOT EXISTS order_performance (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                        VARCHAR(64) NOT NULL,
    settlement_order_id             UUID,
    default_channel_id              UUID,
    default_recruiter_id            UUID,
    final_channel_id                UUID,
    final_recruiter_id              UUID,
    pay_amount                      BIGINT DEFAULT 0,
    settle_amount                   BIGINT DEFAULT 0,
    estimate_service_fee            BIGINT DEFAULT 0,
    effective_service_fee           BIGINT DEFAULT 0,
    estimate_tech_service_fee       BIGINT DEFAULT 0,
    effective_tech_service_fee        BIGINT DEFAULT 0,
    estimate_service_profit         BIGINT DEFAULT 0,
    effective_service_profit        BIGINT DEFAULT 0,
    estimate_recruiter_commission   BIGINT DEFAULT 0,
    effective_recruiter_commission  BIGINT DEFAULT 0,
    estimate_channel_commission     BIGINT DEFAULT 0,
    effective_channel_commission    BIGINT DEFAULT 0,
    recruiter_commission_rate       NUMERIC(8, 4),
    channel_commission_rate         NUMERIC(8, 4),
    is_valid                        SMALLINT NOT NULL DEFAULT 1,
    is_reversed                     SMALLINT NOT NULL DEFAULT 0,
    calculated_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time                     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                         SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_performance_order_id UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_order_performance_channel ON order_performance (default_channel_id);
CREATE INDEX IF NOT EXISTS idx_order_performance_recruiter ON order_performance (default_recruiter_id);
CREATE INDEX IF NOT EXISTS idx_order_performance_calculated_at ON order_performance (calculated_at);
