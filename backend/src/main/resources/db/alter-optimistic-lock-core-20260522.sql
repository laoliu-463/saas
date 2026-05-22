-- LCK-01: 核心并发写表乐观锁 version 列（幂等）
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN talent.version IS '乐观锁版本号';
COMMENT ON COLUMN product_operation_state.version IS '乐观锁版本号';
COMMENT ON COLUMN pick_source_mapping.version IS '乐观锁版本号';
COMMENT ON COLUMN talent_claim.version IS '乐观锁版本号';
COMMENT ON COLUMN sample_request.version IS '乐观锁版本号';
COMMENT ON COLUMN merchant.version IS '乐观锁版本号';
COMMENT ON COLUMN colonelsettlement_order.version IS '乐观锁版本号';
