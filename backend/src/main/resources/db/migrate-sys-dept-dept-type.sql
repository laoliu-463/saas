-- sys_dept 补齐 dept_type，并对齐 BIZ / CHANNEL / OPS 种子分类（幂等）
ALTER TABLE sys_dept ADD COLUMN IF NOT EXISTS dept_type VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_sys_dept_dept_type ON sys_dept(dept_type);

UPDATE sys_dept SET dept_type = 'recruiter'
WHERE dept_code = 'BIZ' AND (dept_type IS NULL OR dept_type = '');

UPDATE sys_dept SET dept_type = 'channel'
WHERE dept_code = 'CHANNEL' AND (dept_type IS NULL OR dept_type = '');

UPDATE sys_dept SET dept_type = 'dept'
WHERE dept_code = 'OPS' AND (dept_type IS NULL OR dept_type = '');
