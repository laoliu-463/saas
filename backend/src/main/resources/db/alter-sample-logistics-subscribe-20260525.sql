-- 快递100订阅推送字段：仍挂在寄样履约链路，不新增独立物流业务域

ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS logistics_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_subscribe_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_subscribed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_subscribe_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_callback_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_callback_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_callback_message TEXT,
    ADD COLUMN IF NOT EXISTS logistics_exception_reason TEXT;

ALTER TABLE sample_logistics_trace
    ADD COLUMN IF NOT EXISTS node_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS location VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_logistics_trace_request_node
    ON sample_logistics_trace (sample_request_id, node_hash)
    WHERE node_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sample_request_logistics_callback
    ON sample_request (status, logistics_last_callback_at)
    WHERE deleted = 0 AND tracking_no IS NOT NULL AND tracking_no <> '';
