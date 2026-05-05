ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN DEFAULT FALSE;

ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklist_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_talent_blacklisted
    ON talent(blacklisted);

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_at TIMESTAMP;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_by UUID;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS audit_payload TEXT;

CREATE INDEX IF NOT EXISTS idx_product_operation_state_selected_to_library
    ON product_operation_state(selected_to_library);
