-- 订单域只保存默认归属；渠道与招商维度必须独立表达，兼容旧聚合状态。
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_attribution_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recruiter_attribution_status VARCHAR(32);

UPDATE colonelsettlement_order
SET channel_attribution_status = CASE
        WHEN channel_user_id IS NOT NULL THEN 'ATTRIBUTED'
        ELSE 'UNATTRIBUTED'
    END,
    recruiter_attribution_status = CASE
        WHEN colonel_user_id IS NOT NULL THEN 'ATTRIBUTED'
        ELSE 'UNATTRIBUTED'
    END
WHERE channel_attribution_status IS NULL
   OR recruiter_attribution_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_colonelsettlement_order_channel_attribution_status
    ON colonelsettlement_order (channel_attribution_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_colonelsettlement_order_recruiter_attribution_status
    ON colonelsettlement_order (recruiter_attribution_status)
    WHERE deleted = 0;
