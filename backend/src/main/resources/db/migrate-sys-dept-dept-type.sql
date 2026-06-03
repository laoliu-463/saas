-- sys_dept 补齐 dept_type，并对齐 BIZ / CHANNEL / OPS 种子分类（幂等）
ALTER TABLE sys_dept ADD COLUMN IF NOT EXISTS dept_type VARCHAR(32);
ALTER TABLE sys_dept ALTER COLUMN dept_type TYPE VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_sys_dept_dept_type ON sys_dept(dept_type);

UPDATE sys_dept SET dept_type = 'recruiter_group'
WHERE dept_type = 'recruiter';

UPDATE sys_dept SET dept_type = 'channel_group'
WHERE dept_type = 'channel';

UPDATE sys_dept SET dept_type = 'department'
WHERE dept_type = 'dept';

UPDATE sys_dept SET dept_type = 'recruiter_group'
WHERE dept_code = 'BIZ' AND (dept_type IS NULL OR dept_type IN ('', 'BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'channel_group'
WHERE dept_code = 'CHANNEL' AND (dept_type IS NULL OR dept_type IN ('', 'BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'ops_group'
WHERE dept_code = 'OPS' AND (dept_type IS NULL OR dept_type IN ('', 'BUSINESS', 'department'));

ALTER TABLE sys_dept ALTER COLUMN dept_type SET DEFAULT 'department';
