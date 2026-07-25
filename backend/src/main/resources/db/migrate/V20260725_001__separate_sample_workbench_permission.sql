-- Separate the cooperation workbench UI permission from the sample API
-- permission. Operations staff still need sample:access for the shipping
-- workflow, but must not be presented with the business cooperation page.
DO $$
BEGIN
    IF to_regclass('public.sys_permission') IS NULL
       OR to_regclass('public.sys_role_permission') IS NULL
       OR to_regclass('public.sys_role') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO sys_permission (
        permission_code,
        domain_code,
        resource_code,
        action_code,
        data_scope_required,
        status,
        deleted,
        remark
    )
    VALUES (
        'sample:workbench',
        'system',
        'sample',
        'workbench',
        FALSE,
        1,
        0,
        'Business cooperation workbench UI access'
    )
    ON CONFLICT (permission_code) DO UPDATE
        SET domain_code = EXCLUDED.domain_code,
            resource_code = EXCLUDED.resource_code,
            action_code = EXCLUDED.action_code,
            data_scope_required = EXCLUDED.data_scope_required,
            status = EXCLUDED.status,
            deleted = EXCLUDED.deleted,
            remark = EXCLUDED.remark,
            update_time = CURRENT_TIMESTAMP;

    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, p.id
    FROM (VALUES
        ('admin'),
        ('biz_leader'),
        ('biz_staff'),
        ('channel_leader'),
        ('channel_staff')
    ) AS seed(role_code)
    JOIN sys_role r ON r.role_code = seed.role_code AND r.deleted = 0
    CROSS JOIN sys_permission p
    WHERE p.permission_code = 'sample:workbench'
    ON CONFLICT (role_id, permission_id) DO NOTHING;
END
$$;
