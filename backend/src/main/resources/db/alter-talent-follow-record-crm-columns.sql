-- Align talent_follow_record with TalentFollowRecord entity (product CRM follow-up).
-- Safe on databases created from older migrate-all.sql (user_id / status schema).

ALTER TABLE talent_follow_record
    ADD COLUMN IF NOT EXISTS activity_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS talent_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS follow_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS next_follow_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS operator_id UUID,
    ADD COLUMN IF NOT EXISTS operator_name VARCHAR(255);

ALTER TABLE talent_follow_record
    ALTER COLUMN talent_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_talent_follow_record_activity_product
    ON talent_follow_record (activity_id, product_id, create_time DESC);

