CREATE TABLE IF NOT EXISTS talent_follow_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       VARCHAR(64) NOT NULL,
    activity_id      VARCHAR(64) NOT NULL,
    talent_id        UUID,
    talent_name      VARCHAR(255),
    follow_status    VARCHAR(64) NOT NULL,
    content          TEXT,
    next_follow_time TIMESTAMP,
    operator_id      UUID,
    operator_name    VARCHAR(255),
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID
);

CREATE INDEX IF NOT EXISTS idx_talent_follow_record_activity_product
    ON talent_follow_record(activity_id, product_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_talent_follow_record_talent_id
    ON talent_follow_record(talent_id);
