-- =============================================
-- 业绩域 DDD-DUAL-ATTRIBUTION-STATUS 升级
-- 把 OrderDefaultAttributionResult 的双维度归属状态
-- 持久化到 colonelsettlement_order
-- =============================================
-- 前置：已有 alter-order-attribution-mvp.sql（attribution_status / attribution_remark）
--       + alter-order-attribution-mvp-v2.sql（colonel_user_id / channel_user_id / channel_dept_id）

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_attribution_status VARCHAR(32) DEFAULT 'CHANNEL_UNATTRIBUTED';

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS recruiter_attribution_status VARCHAR(32) DEFAULT 'RECRUITER_UNATTRIBUTED';

COMMENT ON COLUMN colonelsettlement_order.channel_attribution_status
    IS '渠道归属状态：CHANNEL_ATTRIBUTED / CHANNEL_UNATTRIBUTED，由 OrderDefaultAttributionPolicy.applyToOrder 写入';
COMMENT ON COLUMN colonelsettlement_order.recruiter_attribution_status
    IS '招商归属状态：RECRUITER_ATTRIBUTED / RECRUITER_UNATTRIBUTED，由 OrderDefaultAttributionPolicy.applyToOrder 写入';

-- 历史回填：保持兼容，不假设历史订单能区分双维度状态；显式落到未归属。
UPDATE colonelsettlement_order
SET channel_attribution_status = 'CHANNEL_UNATTRIBUTED'
WHERE channel_attribution_status IS NULL;

UPDATE colonelsettlement_order
SET recruiter_attribution_status = 'RECRUITER_UNATTRIBUTED'
WHERE recruiter_attribution_status IS NULL;

-- 部分索引：与 attribution_status 索引保持一致风格
CREATE INDEX IF NOT EXISTS idx_cso_channel_attribution_status
    ON colonelsettlement_order (channel_attribution_status);
CREATE INDEX IF NOT EXISTS idx_cso_recruiter_attribution_status
    ON colonelsettlement_order (recruiter_attribution_status);
