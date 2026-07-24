-- Keep the real-pre permission catalog aligned with the product contract:
-- channel roles may view and operate their scoped samples, but cannot export.
DELETE FROM sys_role_permission rp
USING sys_role r, sys_permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.role_code IN ('channel_leader', 'channel_staff')
  AND p.permission_code = 'sample:export-samples';
