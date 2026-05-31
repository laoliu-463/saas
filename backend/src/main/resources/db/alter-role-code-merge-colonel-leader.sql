-- Merge legacy role_code colonel_leader into biz_leader (idempotent).
-- V1 角色编码治理：2026-05-30

-- 1) Merge menus: colonel_leader -> biz_leader
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT biz.id, crm.menu_id
FROM sys_role old_role
         JOIN sys_role biz ON biz.role_code = 'biz_leader' AND COALESCE(biz.deleted, 0) = 0
         JOIN sys_role_menu crm ON crm.role_id = old_role.id
WHERE old_role.role_code = 'colonel_leader'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 2) Ensure users with colonel_leader also have biz_leader
INSERT INTO sys_user_role (user_id, role_id, deleted)
SELECT DISTINCT ur.user_id, biz.id, 0
FROM sys_user_role ur
         JOIN sys_role old_role ON old_role.id = ur.role_id AND old_role.role_code = 'colonel_leader'
         JOIN sys_role biz ON biz.role_code = 'biz_leader' AND COALESCE(biz.deleted, 0) = 0
WHERE COALESCE(ur.deleted, 0) = 0
ON CONFLICT (user_id, role_id) DO UPDATE SET deleted = 0;

-- 3) Soft-delete colonel_leader assignments
UPDATE sys_user_role ur
SET deleted = 1
FROM sys_role old_role
WHERE ur.role_id = old_role.id
  AND old_role.role_code = 'colonel_leader'
  AND COALESCE(ur.deleted, 0) = 0;

-- 4) Soft-delete colonel_leader role row
UPDATE sys_role
SET deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'colonel_leader'
  AND COALESCE(deleted, 0) = 0;
