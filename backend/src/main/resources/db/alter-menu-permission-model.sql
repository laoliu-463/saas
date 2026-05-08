-- ============================================================
-- Gap 1: 菜单/操作权限模型 — sys_menu + sys_role_menu
-- ============================================================

-- sys_menu 表
CREATE TABLE IF NOT EXISTS sys_menu (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_name       VARCHAR(100) NOT NULL,
    menu_type       VARCHAR(10)  NOT NULL DEFAULT 'MENU',   -- MENU / BUTTON / API
    parent_id       UUID         DEFAULT '00000000-0000-0000-0000-000000000000',
    path            VARCHAR(200),
    component       VARCHAR(200),
    icon            VARCHAR(100),
    sort_order      INT          DEFAULT 0,
    permission_code VARCHAR(100),
    visible         SMALLINT     DEFAULT 1,
    status          SMALLINT     DEFAULT 1,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID
);

CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_id ON sys_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_status ON sys_menu (status);

-- sys_role_menu 关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id UUID NOT NULL,
    menu_id UUID NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);

-- 初始化默认菜单数据
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '系统管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/system', null, 'setting', 90, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 子菜单: 用户管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '用户管理', 'MENU', m.id, '/system/user', 'system/UserManagement', 'user', 1, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
ON CONFLICT DO NOTHING;

-- 子菜单: 角色管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '角色管理', 'MENU', m.id, '/system/role', 'system/RoleManagement', 'team', 2, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
ON CONFLICT DO NOTHING;

-- 子菜单: 菜单管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '菜单管理', 'MENU', m.id, '/system/menu', 'system/MenuManagement', 'menu', 3, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
ON CONFLICT DO NOTHING;

-- 子菜单: 操作日志
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '操作日志', 'MENU', m.id, '/system/logs', 'system/OperationLog', 'file-text', 4, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
ON CONFLICT DO NOTHING;

-- 业务菜单: 达人管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '达人管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/talent', null, 'star', 10, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 业务菜单: 商品管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '商品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/product', null, 'shopping', 20, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 菜单口径收口：/product 作为商品库，活动链路拆到 /product/manage
UPDATE sys_menu
SET menu_name = '商品库',
    path = '/product',
    update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND parent_id = '00000000-0000-0000-0000-000000000000'
  AND menu_name = '商品管理'
  AND path = '/product';

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '商品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/product/manage', null, 'shopping', 21, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 业务菜单: 订单管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '订单管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/order', null, 'file-done', 30, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 业务菜单: 样品管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '样品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/sample', null, 'gift', 40, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 业务菜单: 数据看板
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '数据看板', 'MENU', '00000000-0000-0000-0000-000000000000', '/dashboard', null, 'bar-chart', 50, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 业务菜单: 活动管理
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
VALUES
    (gen_random_uuid(), '活动管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/activity', null, 'trophy', 60, null, 1, 1)
ON CONFLICT DO NOTHING;

-- 为 admin 角色分配全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'admin' AND m.deleted = 0
ON CONFLICT DO NOTHING;
