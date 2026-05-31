-- Adds payment/order-created columns to colonelsettlement_order.
-- pay_time is real payment success time only; do not backfill it from create_time/order_create_time.
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS pay_time TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS order_create_time TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_cso_pay_time ON colonelsettlement_order (pay_time);
CREATE INDEX IF NOT EXISTS idx_cso_order_create_time ON colonelsettlement_order (order_create_time);
