ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS talent_id UUID;

CREATE INDEX IF NOT EXISTS idx_cso_talent_id
    ON colonelsettlement_order(talent_id);
