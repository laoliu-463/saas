-- 活动商品 full backfill 审计表与 activity 级 checkpoint。
CREATE TABLE IF NOT EXISTS product_sync_job_log (
    id UUID PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    job_type VARCHAR(80) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL,
    requested_by UUID,
    request_params_json TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    activities_scanned INTEGER DEFAULT 0,
    activities_success INTEGER DEFAULT 0,
    activities_incomplete INTEGER DEFAULT 0,
    activities_failed INTEGER DEFAULT 0,
    api_fetched_rows BIGINT DEFAULT 0,
    api_distinct_product_ids BIGINT DEFAULT 0,
    inserted INTEGER DEFAULT 0,
    updated INTEGER DEFAULT 0,
    skipped INTEGER DEFAULT 0,
    failed INTEGER DEFAULT 0,
    stop_reason_stats_json TEXT,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    deleted INTEGER DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_sync_job_log_job_id
    ON product_sync_job_log(job_id);

CREATE INDEX IF NOT EXISTS idx_product_sync_job_log_type_started
    ON product_sync_job_log(job_type, started_at DESC);

CREATE TABLE IF NOT EXISTS product_activity_sync_state (
    id UUID PRIMARY KEY,
    activity_id VARCHAR(64) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    last_success_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    last_status VARCHAR(50),
    last_stop_reason VARCHAR(50),
    last_cursor TEXT,
    last_page INTEGER,
    last_fetched_rows BIGINT DEFAULT 0,
    last_distinct_product_ids BIGINT DEFAULT 0,
    last_inserted INTEGER DEFAULT 0,
    last_updated INTEGER DEFAULT 0,
    last_skipped INTEGER DEFAULT 0,
    last_failed INTEGER DEFAULT 0,
    consecutive_failures INTEGER DEFAULT 0,
    last_error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    deleted INTEGER DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_activity_sync_state_activity_scope
    ON product_activity_sync_state(activity_id, scope);

CREATE INDEX IF NOT EXISTS idx_product_activity_sync_state_status
    ON product_activity_sync_state(last_status, last_attempt_at DESC);
