-- =============================================
-- colonel_activity 招商负责人分配字段 Flyway Migration
-- 适用于全新环境或需要 Flyway 管理的场景
-- 日期: 2026-05-29
-- =============================================

-- 活动级招商组长用户 ID（null 表示未分配）
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS recruiter_user_id UUID;

-- 活动级招商组长部门 ID
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS recruiter_dept_id UUID;

-- 活动分配给招商组长时间
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

-- 执行活动分配的管理员用户 ID
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS assigned_by UUID;

-- 抖店活动状态码（5=推广中），用于推广中判定
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS activity_status_code INTEGER;

-- 抖店活动状态文案（如「推广中」），用于推广中判定
ALTER TABLE colonel_activity ADD COLUMN IF NOT EXISTS activity_status_text VARCHAR(64);

-- 添加索引以提升按招商负责人查询的性能
CREATE INDEX IF NOT EXISTS idx_activity_recruiter_user ON colonel_activity(recruiter_user_id);
CREATE INDEX IF NOT EXISTS idx_activity_status_code ON colonel_activity(activity_status_code);

-- 字段注释
COMMENT ON COLUMN colonel_activity.recruiter_user_id IS '活动级招商组长用户ID';
COMMENT ON COLUMN colonel_activity.recruiter_dept_id IS '活动级招商组长部门ID';
COMMENT ON COLUMN colonel_activity.assigned_at IS '活动分配时间';
COMMENT ON COLUMN colonel_activity.assigned_by IS '活动分配操作人';
COMMENT ON COLUMN colonel_activity.activity_status_code IS '抖店活动状态码(5=推广中)';
COMMENT ON COLUMN colonel_activity.activity_status_text IS '抖店活动状态文案';
