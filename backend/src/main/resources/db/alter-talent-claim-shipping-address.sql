-- 认领记录收货地址 + 最近出单时间（达人域 T-04 / T-03）
-- 执行前置：init-db.sql

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS recipient_name    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_phone   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS last_order_at     TIMESTAMP;
