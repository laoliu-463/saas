-- 持久化 colonel_activity.status 字段（活动状态码）
-- 用途：推广中活动(status=5)自动触发 forceDisplay 全量展示商品库
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS status INTEGER;
COMMENT ON COLUMN colonel_activity.status IS '活动状态码(1=未上线,2=报名未开始,3=报名中,4=推广未开始,5=推广中,7=已结束)';
