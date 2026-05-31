ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS pay_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS order_create_time TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_cso_pay_time ON colonelsettlement_order (pay_time);
CREATE INDEX IF NOT EXISTS idx_cso_order_create_time ON colonelsettlement_order (order_create_time);

CREATE TABLE IF NOT EXISTS colonel_order_settlement (
    id                               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    upstream_key                     VARCHAR(160) NOT NULL,
    order_id                         VARCHAR(64) NOT NULL,
    product_id                       VARCHAR(64),
    product_name                     VARCHAR(500),
    shop_id                          BIGINT,
    shop_name                        VARCHAR(200),
    pay_amount                       BIGINT DEFAULT 0,
    settle_amount                    BIGINT DEFAULT 0,
    estimate_service_fee             BIGINT DEFAULT 0,
    effective_service_fee            BIGINT DEFAULT 0,
    estimate_tech_service_fee        BIGINT DEFAULT 0,
    effective_tech_service_fee       BIGINT DEFAULT 0,
    settle_second_colonel_commission BIGINT DEFAULT 0,
    source_amount_unit               VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    colonel_buyin_id                 BIGINT,
    colonel_name                     VARCHAR(200),
    service_fee_rate                 NUMERIC(10,4),
    commission_rate                  NUMERIC(10,4),
    colonel_activity_id              VARCHAR(64),
    second_colonel_buyin_id          BIGINT,
    second_colonel_activity_id       VARCHAR(64),
    phase_id                         VARCHAR(64),
    pick_source                      VARCHAR(128),
    talent_external_id               VARCHAR(64),
    talent_name                      VARCHAR(200),
    flow_point                       VARCHAR(64),
    order_status                     SMALLINT,
    order_create_time                TIMESTAMP,
    pay_time                         TIMESTAMP,
    settle_time                      TIMESTAMP,
    delivery_time                    TIMESTAMP,
    expire_time                      TIMESTAMP,
    cursor                           VARCHAR(100),
    create_time                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                          SMALLINT NOT NULL DEFAULT 0,
    raw_payload                      JSONB,
    CONSTRAINT uq_colonel_order_settlement_upstream_key UNIQUE (upstream_key)
);

CREATE INDEX IF NOT EXISTS idx_cos_order_id ON colonel_order_settlement (order_id);
CREATE INDEX IF NOT EXISTS idx_cos_settle_time ON colonel_order_settlement (settle_time);
CREATE INDEX IF NOT EXISTS idx_cos_pay_time ON colonel_order_settlement (pay_time);
CREATE INDEX IF NOT EXISTS idx_cos_activity_id ON colonel_order_settlement (colonel_activity_id);
CREATE INDEX IF NOT EXISTS idx_cos_shop_id ON colonel_order_settlement (shop_id);

ALTER TABLE colonel_order_settlement
    ADD COLUMN IF NOT EXISTS colonel_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS service_fee_rate NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS source_amount_unit VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED';
