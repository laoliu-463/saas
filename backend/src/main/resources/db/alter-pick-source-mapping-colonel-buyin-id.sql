-- 抖店原生订单回流会返回 19 位 colonel_buyin_id，不能复用 8-10 位 short_id。
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS colonel_buyin_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_psm_colonel_buyin_id
    ON pick_source_mapping(colonel_buyin_id);
