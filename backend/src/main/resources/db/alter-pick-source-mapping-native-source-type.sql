ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'PICK_SOURCE';

UPDATE pick_source_mapping
SET source_type = CASE
    WHEN colonel_buyin_id IS NOT NULL
         AND (pick_source IS NULL OR pick_source LIKE 'colonel_native_%')
        THEN 'NATIVE'
    ELSE 'PICK_SOURCE'
END
WHERE source_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_psm_source_type
    ON pick_source_mapping(source_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_psm_native_activity_product_user
    ON pick_source_mapping(colonel_buyin_id, product_id, activity_id, user_id, source_type)
    WHERE deleted = 0 AND source_type = 'NATIVE';
