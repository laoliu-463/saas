-- 用户域 V1 验收：待激活状态机 + sys_dept 组别语义
-- 可单独执行，已合并进 migrate-all.sql

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS force_password_change BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS leader_user_id UUID;

UPDATE sys_dept SET dept_type = 'recruiter_group'
WHERE dept_code = 'BIZ' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'channel_group'
WHERE dept_code = 'CHANNEL' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'ops_group'
WHERE dept_code = 'OPS' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

ALTER TABLE sys_dept ALTER COLUMN dept_type SET DEFAULT 'department';
