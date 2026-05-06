-- 商家归属人支持：增加 owner_id / owner_dept_id 列
ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS owner_id   VARCHAR(36),
    ADD COLUMN IF NOT EXISTS owner_dept_id VARCHAR(36);
