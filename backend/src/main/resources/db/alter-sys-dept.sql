-- =============================================
-- sys_dept 业务组表补齐（棕地增量）
-- =============================================

CREATE TABLE IF NOT EXISTS sys_dept (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     UUID,
    dept_code     VARCHAR(50) NOT NULL UNIQUE,
    dept_name     VARCHAR(100) NOT NULL,
    leader        VARCHAR(100),
    phone         VARCHAR(20),
    email         VARCHAR(100),
    sort_order    INT       NOT NULL DEFAULT 0,
    status        SMALLINT  NOT NULL DEFAULT 1,
    deleted       SMALLINT  NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    remark        VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sys_dept_parent_id ON sys_dept(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_dept_status    ON sys_dept(status);
CREATE INDEX IF NOT EXISTS idx_sys_dept_deleted   ON sys_dept(deleted);

INSERT INTO sys_dept (id, parent_id, dept_code, dept_name, dept_type, sort_order, status)
VALUES
    ('a2b3c4d5-e6f7-4890-abcd-ef0123456789', NULL, 'BIZ', '招商部', 'department', 10, 1),
    ('b3c4d5e6-f7a8-4901-bcde-f01234567890', NULL, 'CHANNEL', '渠道部', 'department', 20, 1),
    ('c4d5e6f7-a8b9-4012-cdef-012345678901', NULL, 'OPS', '运营部', 'department', 30, 1)
ON CONFLICT (dept_code) DO NOTHING;
