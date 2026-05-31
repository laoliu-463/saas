-- ops_staff 只在寄样模块内享受 all 数据范围；登录态保持角色配置 data_scope=1。
UPDATE sys_role
SET data_scope = 1
WHERE role_code = 'ops_staff'
  AND COALESCE(data_scope, 0) <> 1;
