-- 寄样域物流同步字段与轨迹表（V2.0 收尾增强）

ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS logistics_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_status_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS logistics_last_query_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_error TEXT,
    ADD COLUMN IF NOT EXISTS logistics_raw_payload JSONB,
    ADD COLUMN IF NOT EXISTS signed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS external_last_sync_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_sample_request_logistics_sync
    ON sample_request (status, logistics_last_query_at)
    WHERE deleted = 0 AND tracking_no IS NOT NULL AND tracking_no <> '';

CREATE TABLE IF NOT EXISTS sample_logistics_trace (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_request_id   UUID NOT NULL,
    tracking_no         VARCHAR(100),
    logistics_company   VARCHAR(50),
    status_code         VARCHAR(32),
    status_name         VARCHAR(64),
    trace_time          TIMESTAMP,
    trace_content       TEXT,
    raw_payload         JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sample_logistics_trace_request
    ON sample_logistics_trace (sample_request_id, trace_time DESC);
