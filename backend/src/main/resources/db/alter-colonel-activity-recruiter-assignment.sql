-- 活动级招商组长分配 + 抖店活动状态码/文案持久化
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS activity_status_code INTEGER;
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS activity_status_text VARCHAR(64);
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS recruiter_user_id UUID;
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS recruiter_dept_id UUID;
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS assigned_by UUID;

COMMENT ON COLUMN colonel_activity.activity_status_code IS '抖店活动状态码(5=推广中)';
COMMENT ON COLUMN colonel_activity.activity_status_text IS '抖店活动状态文案';
COMMENT ON COLUMN colonel_activity.recruiter_user_id IS '活动级招商组长用户ID';
COMMENT ON COLUMN colonel_activity.recruiter_dept_id IS '活动级招商组长部门ID';
COMMENT ON COLUMN colonel_activity.assigned_at IS '活动分配时间';
COMMENT ON COLUMN colonel_activity.assigned_by IS '活动分配操作人';

CREATE INDEX IF NOT EXISTS idx_activity_recruiter_user ON colonel_activity(recruiter_user_id);
CREATE INDEX IF NOT EXISTS idx_activity_status_code ON colonel_activity(activity_status_code);
