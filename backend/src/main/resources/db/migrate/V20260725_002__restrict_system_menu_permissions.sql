-- Keep system administration permissions out of business roles.
-- Channel leaders use scoped group-member APIs, not the system department UI.
DO $$
BEGIN
    IF to_regclass('public.sys_role_permission') IS NULL
       OR to_regclass('public.sys_role') IS NULL
       OR to_regclass('public.sys_permission') IS NULL THEN
        RETURN;
    END IF;

    DELETE FROM sys_role_permission rp
    USING sys_role r, sys_permission p
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND r.role_code = 'channel_leader'
      AND p.permission_code = 'sys-dept:access';
END
$$;
