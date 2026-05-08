ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS owner_id UUID,
    ADD COLUMN IF NOT EXISTS owner_dept_id UUID;

CREATE INDEX IF NOT EXISTS idx_merchant_owner_id
    ON merchant(owner_id);

CREATE INDEX IF NOT EXISTS idx_merchant_owner_dept_id
    ON merchant(owner_dept_id);
