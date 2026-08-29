ALTER TABLE promotion_link
    ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_attribution_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recruiter_attribution_source VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_promotion_link_attribution_owner_type'
          AND conrelid = 'promotion_link'::regclass
    ) THEN
        ALTER TABLE promotion_link
            ADD CONSTRAINT chk_promotion_link_attribution_owner_type
            CHECK (attribution_owner_type IS NULL
                OR attribution_owner_type IN ('CHANNEL', 'RECRUITER'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_pick_source_mapping_attribution_owner_type'
          AND conrelid = 'pick_source_mapping'::regclass
    ) THEN
        ALTER TABLE pick_source_mapping
            ADD CONSTRAINT chk_pick_source_mapping_attribution_owner_type
            CHECK (attribution_owner_type IS NULL
                OR attribution_owner_type IN ('CHANNEL', 'RECRUITER'));
    END IF;
END $$;
