ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_status VARCHAR(32) DEFAULT 'UNATTRIBUTED';

ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_remark VARCHAR(255);

ALTER TABLE colonelsettlement_order
ALTER COLUMN pick_source TYPE VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_cso_attribution_status
    ON colonelsettlement_order(attribution_status);
