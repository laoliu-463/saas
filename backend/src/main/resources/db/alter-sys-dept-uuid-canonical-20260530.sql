-- =============================================
-- sys_dept 规范化迁移（2026-05-30）
-- =============================================
-- 背景：此前 dept 使用硬编码的顺序 UUID（如 11111111...），
--       且部门名称沿用了旧的"招商组/渠道组/运营组"命名。
-- 改动：
--   1. 部门 UUID 替换为标准 v4 格式
--   2. 部门名称统一去掉"组"字后缀（招商部 / 渠道部 / 运营部）
--   3. sys_dept.dept_type 统一为 'department'
--   4. sys_user.dept_id 同步更新
--
-- 旧 → 新 UUID 映射：
--   BIZ     (招商): 11111111-1111-1111-1111-111111111111 → a2b3c4d5-e6f7-4890-abcd-ef0123456789
--   CHANNEL (渠道): 22222222-2222-2222-2222-222222222222 → b3c4d5e6-f7a8-4901-bcde-f01234567890
--   OPS    (运营): 33333333-3333-3333-3333-333333333333 → c4d5e6f7-a8b9-4012-cdef-012345678901

BEGIN;

-- 替换部门 UUID
UPDATE sys_dept SET id = 'a2b3c4d5-e6f7-4890-abcd-ef0123456789' WHERE dept_code = 'BIZ';
UPDATE sys_dept SET id = 'b3c4d5e6-f7a8-4901-bcde-f01234567890' WHERE dept_code = 'CHANNEL';
UPDATE sys_dept SET id = 'c4d5e6f7-a8b9-4012-cdef-012345678901' WHERE dept_code = 'OPS';

-- 统一部门名称
UPDATE sys_dept SET dept_name = '招商部' WHERE dept_code = 'BIZ';
UPDATE sys_dept SET dept_name = '渠道部' WHERE dept_code = 'CHANNEL';
UPDATE sys_dept SET dept_name = '运营部' WHERE dept_code = 'OPS';

-- 统一 dept_type
UPDATE sys_dept SET dept_type = 'department' WHERE dept_code IN ('BIZ', 'CHANNEL', 'OPS');

-- 同步用户 dept_id（按 username 而非 dept_id 防止迁移顺序问题）
UPDATE sys_user SET dept_id = 'a2b3c4d5-e6f7-4890-abcd-ef0123456789'
WHERE username IN ('biz_leader', 'biz_staff') AND deleted = 0;

UPDATE sys_user SET dept_id = 'b3c4d5e6-f7a8-4901-bcde-f01234567890'
WHERE username IN ('channel_leader', 'channel_staff') AND deleted = 0;

UPDATE sys_user SET dept_id = 'c4d5e6f7-a8b9-4012-cdef-012345678901'
WHERE username = 'ops_staff' AND deleted = 0;

COMMIT;
