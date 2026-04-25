ALTER TABLE product_operation_state
ADD COLUMN IF NOT EXISTS biz_status VARCHAR(64);

ALTER TABLE product_operation_log
ADD COLUMN IF NOT EXISTS before_status VARCHAR(64);

ALTER TABLE product_operation_log
ADD COLUMN IF NOT EXISTS after_status VARCHAR(64);

ALTER TABLE product_operation_log
ADD COLUMN IF NOT EXISTS success BOOLEAN DEFAULT TRUE;

ALTER TABLE product_operation_log
ADD COLUMN IF NOT EXISTS error_message TEXT;

UPDATE product_operation_state
SET biz_status = CASE
    WHEN audit_status = 2 THEN 'APPROVED'
    WHEN audit_status = 3 THEN 'REJECTED'
    ELSE 'PENDING_AUDIT'
END
WHERE biz_status IS NULL;
