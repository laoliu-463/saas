-- Role-aware attribution schema contract.
-- This migration is deliberately additive and safe for databases that were
-- initialized from older docker-entrypoint-initdb.d scripts.

ALTER TABLE IF EXISTS public.colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_attribution_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recruiter_attribution_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS channel_attribution_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recruiter_attribution_source VARCHAR(64);

ALTER TABLE IF EXISTS public.promotion_link
    ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);

ALTER TABLE IF EXISTS public.pick_source_mapping
    ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);

DO $$
BEGIN
    IF to_regclass('public.promotion_link') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'chk_promotion_link_attribution_owner_type'
             AND conrelid = to_regclass('public.promotion_link')
       ) THEN
        ALTER TABLE public.promotion_link
            ADD CONSTRAINT chk_promotion_link_attribution_owner_type
            CHECK (attribution_owner_type IS NULL
                OR attribution_owner_type IN ('CHANNEL', 'RECRUITER'));
    END IF;

    IF to_regclass('public.pick_source_mapping') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'chk_pick_source_mapping_attribution_owner_type'
             AND conrelid = to_regclass('public.pick_source_mapping')
       ) THEN
        ALTER TABLE public.pick_source_mapping
            ADD CONSTRAINT chk_pick_source_mapping_attribution_owner_type
            CHECK (attribution_owner_type IS NULL
                OR attribution_owner_type IN ('CHANNEL', 'RECRUITER'));
    END IF;
END $$;
