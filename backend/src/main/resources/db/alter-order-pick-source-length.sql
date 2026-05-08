ALTER TABLE colonelsettlement_order
    ALTER COLUMN pick_source TYPE VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_psm_pick_extra
    ON pick_source_mapping(pick_extra);
