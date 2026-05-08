-- 商家归属人支持：增加 owner_id / owner_dept_id 列，并统一为 UUID 口径
ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS owner_id UUID,
    ADD COLUMN IF NOT EXISTS owner_dept_id UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_id TYPE UUID
    USING NULLIF(TRIM(owner_id::text), '')::UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_dept_id TYPE UUID
    USING NULLIF(TRIM(owner_dept_id::text), '')::UUID;

CREATE INDEX IF NOT EXISTS idx_merchant_owner_id ON merchant(owner_id);
CREATE INDEX IF NOT EXISTS idx_merchant_owner_dept ON merchant(owner_dept_id);
