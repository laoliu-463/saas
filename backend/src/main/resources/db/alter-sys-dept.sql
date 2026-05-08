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

INSERT INTO sys_dept (id, parent_id, dept_code, dept_name, sort_order, status)
VALUES
    ('11111111-1111-1111-1111-111111111111', NULL, 'BIZ', '招商组', 10, 1),
    ('22222222-2222-2222-2222-222222222222', NULL, 'CHANNEL', '渠道组', 20, 1),
    ('33333333-3333-3333-3333-333333333333', NULL, 'OPS', '运营组', 30, 1)
ON CONFLICT (dept_code) DO NOTHING;
