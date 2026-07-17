-- =============================================
-- 业绩域 DDD-DUAL-ATTRIBUTION-STATUS 升级
-- 把 OrderDefaultAttributionResult 的双维度归属状态
-- 持久化到 colonelsettlement_order
-- =============================================
-- 前置：已有 alter-order-attribution-mvp.sql（attribution_status / attribution_remark）
--       + alter-order-attribution-mvp-v2.sql（colonel_user_id / channel_user_id / channel_dept_id）

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS channel_attribution_status VARCHAR(32);

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS recruiter_attribution_status VARCHAR(32);

ALTER TABLE colonelsettlement_order
    ALTER COLUMN channel_attribution_status DROP DEFAULT;

ALTER TABLE colonelsettlement_order
    ALTER COLUMN recruiter_attribution_status DROP DEFAULT;

COMMENT ON COLUMN colonelsettlement_order.channel_attribution_status
    IS '渠道归属状态：CHANNEL_ATTRIBUTED / CHANNEL_UNATTRIBUTED，由 OrderDefaultAttributionPolicy.applyToOrder 写入';
COMMENT ON COLUMN colonelsettlement_order.recruiter_attribution_status
    IS '招商归属状态：RECRUITER_ATTRIBUTED / RECRUITER_UNATTRIBUTED，由 OrderDefaultAttributionPolicy.applyToOrder 写入';

-- 历史纠偏：只根据已有 owner 事实确认已归属；无 owner 的旧数据保留 NULL，
-- 由读取兼容层按历史字段推导，避免把未知事实永久覆盖为“未归属”。
UPDATE colonelsettlement_order
SET channel_attribution_status = 'CHANNEL_ATTRIBUTED'
WHERE channel_user_id IS NOT NULL
  AND channel_attribution_status IS DISTINCT FROM 'CHANNEL_ATTRIBUTED';

UPDATE colonelsettlement_order
SET recruiter_attribution_status = 'RECRUITER_ATTRIBUTED'
WHERE colonel_user_id IS NOT NULL
  AND recruiter_attribution_status IS DISTINCT FROM 'RECRUITER_ATTRIBUTED';

-- 部分索引：与 attribution_status 索引保持一致风格
CREATE INDEX IF NOT EXISTS idx_cso_channel_attribution_status
    ON colonelsettlement_order (channel_attribution_status);
CREATE INDEX IF NOT EXISTS idx_cso_recruiter_attribution_status
    ON colonelsettlement_order (recruiter_attribution_status);
