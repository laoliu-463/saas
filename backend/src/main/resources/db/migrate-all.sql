-- =============================================
-- ???? SaaS ?? - ?????????
-- ?? alter-*.sql ?????????????
-- ?????2026-05-21
-- ???init-db.sql ???
-- ???????????? IF NOT EXISTS / IF NOT EXISTS
-- =============================================

\getenv admin_password ADMIN_PASSWORD
\if :{?admin_password}
\else
\echo 'ADMIN_PASSWORD is required for password migration'
\quit 3
\endif


-- ========== 以下为补充合并的增量脚本 DDL（原 migrate-all.sql 遗漏） ==========

-- 来源: alter-product-main-chain.sql
CREATE TABLE IF NOT EXISTS product_snapshot (
    id                      UUID PRIMARY KEY,
    activity_id             VARCHAR(64) NOT NULL,
    product_id              VARCHAR(64) NOT NULL,
    title                   VARCHAR(500),
    cover                   VARCHAR(1000),
    price                   BIGINT,
    price_text              VARCHAR(64),
    shop_id                 BIGINT,
    shop_name               VARCHAR(255),
    status                  INTEGER,
    status_text             VARCHAR(64),
    category_name           VARCHAR(255),
    product_stock           VARCHAR(64),
    sales                   BIGINT,
    detail_url              VARCHAR(1000),
    promotion_start_time    VARCHAR(64),
    promotion_end_time      VARCHAR(64),
    activity_cos_ratio      BIGINT,
    activity_cos_ratio_text VARCHAR(64),
    cos_type                INTEGER,
    cos_type_text           VARCHAR(64),
    ad_service_ratio        VARCHAR(32),
    activity_ad_cos_ratio   BIGINT,
    has_douin_goods_tag     BOOLEAN,
    raw_payload             TEXT,
    sync_time               TIMESTAMP,
    deleted                 SMALLINT  NOT NULL DEFAULT 0,
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               UUID,
    update_by               UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_snapshot_activity_product
    ON product_snapshot(activity_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_activity
    ON product_snapshot(activity_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_product
    ON product_snapshot(product_id);
CREATE INDEX IF NOT EXISTS idx_product_snapshot_deleted
    ON product_snapshot(deleted);

CREATE TABLE IF NOT EXISTS product_operation_state (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id        VARCHAR(64) NOT NULL,
    product_id         VARCHAR(64) NOT NULL,
    bound_activity_id  VARCHAR(64),
    assignee_id        UUID,
    audit_status       INTEGER DEFAULT 0,
    audit_remark       TEXT,
    audit_payload      TEXT,
    promote_link       TEXT,
    short_link         TEXT,
    promotion_scene    INTEGER,
    external_unique_id VARCHAR(128),
    last_operation_at  TIMESTAMP,
    deleted            SMALLINT  NOT NULL DEFAULT 0,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          UUID,
    update_by          UUID,
    version            INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_op_state_activity_product
    ON product_operation_state(activity_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_op_state_assignee
    ON product_operation_state(assignee_id);
CREATE INDEX IF NOT EXISTS idx_product_op_state_deleted
    ON product_operation_state(deleted);

CREATE TABLE IF NOT EXISTS product_operation_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id       VARCHAR(64) NOT NULL,
    product_id        VARCHAR(64) NOT NULL,
    operation_type    VARCHAR(64) NOT NULL,
    operation_payload TEXT,
    operation_remark  VARCHAR(500),
    operator_id       UUID,
    operator_dept_id  UUID,
    deleted           SMALLINT  NOT NULL DEFAULT 0,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         UUID,
    update_by         UUID
);

CREATE INDEX IF NOT EXISTS idx_product_op_log_activity_product
    ON product_operation_log(activity_id, product_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_product_op_log_operator
    ON product_operation_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_product_op_log_deleted
    ON product_operation_log(deleted);

-- 来源: alter-order-attribution-mvp-v2.sql
CREATE TABLE IF NOT EXISTS promotion_link (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        VARCHAR(50)  NOT NULL,
    activity_id       VARCHAR(50),
    talent_id         VARCHAR(50),
    talent_name       VARCHAR(200),
    channel_user_id   UUID,
    channel_user_name VARCHAR(100),
    attribution_owner_type VARCHAR(32),
    original_product_url TEXT,
    promotion_url     TEXT,
    short_url         TEXT,
    doukouling        VARCHAR(200),
    pick_source       VARCHAR(128),
    pick_extra        VARCHAR(128),
    link_status       VARCHAR(20) DEFAULT 'ACTIVE',
    expire_time       TIMESTAMP,
    raw_response      JSONB,
    operator_id       UUID,
    operator_name     VARCHAR(100),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pl_product_id ON promotion_link(product_id);
CREATE INDEX IF NOT EXISTS idx_pl_pick_source ON promotion_link(pick_source);
CREATE INDEX IF NOT EXISTS idx_pl_channel_user ON promotion_link(channel_user_id);

-- 来源: alter-menu-permission-model.sql
CREATE TABLE IF NOT EXISTS sys_menu (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_name       VARCHAR(100) NOT NULL,
    menu_type       VARCHAR(10)  NOT NULL DEFAULT 'MENU',
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

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id UUID NOT NULL,
    menu_id UUID NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);

-- 菜单种子数据 (来源: alter-menu-permission-model.sql)
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '系统管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/system', null, 'setting', 90, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/system'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '用户管理', 'MENU', m.id, '/system/user', 'system/UserManagement', 'user', 1, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE deleted = 0 AND path = '/system/user')
ORDER BY m.create_time ASC
LIMIT 1;

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '角色管理', 'MENU', m.id, '/system/role', 'system/RoleManagement', 'team', 2, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE deleted = 0 AND path = '/system/role')
ORDER BY m.create_time ASC
LIMIT 1;

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '菜单管理', 'MENU', m.id, '/system/menu', 'system/MenuManagement', 'menu', 3, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE deleted = 0 AND path = '/system/menu')
ORDER BY m.create_time ASC
LIMIT 1;

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '操作日志', 'MENU', m.id, '/system/logs', 'system/OperationLog', 'file-text', 4, null, 1, 1
FROM sys_menu m WHERE m.menu_name = '系统管理' AND m.parent_id = '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE deleted = 0 AND path = '/system/logs')
ORDER BY m.create_time ASC
LIMIT 1;

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '达人管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/talent', null, 'star', 10, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/talent'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '商品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/product', null, 'shopping', 20, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/product'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '商品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/product/manage', null, 'shopping', 21, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/product/manage'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '订单管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/order', null, 'file-done', 30, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/order'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '样品管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/sample', null, 'gift', 40, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/sample'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '数据看板', 'MENU', '00000000-0000-0000-0000-000000000000', '/dashboard', null, 'bar-chart', 50, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/dashboard'
);

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission_code, visible, status)
SELECT gen_random_uuid(), '活动管理', 'MENU', '00000000-0000-0000-0000-000000000000', '/activity', null, 'trophy', 60, null, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND parent_id = '00000000-0000-0000-0000-000000000000' AND path = '/activity'
);

-- 为 admin 角色分配全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'admin' AND m.deleted = 0
ON CONFLICT DO NOTHING;

-- 来源: alter-talent-enrich.sql
ALTER TABLE talent ADD COLUMN IF NOT EXISTS douyin_no VARCHAR(100);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS uid VARCHAR(100);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS sec_uid VARCHAR(255);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS profile_url VARCHAR(500);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS enrich_status VARCHAR(50);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS last_enrich_time TIMESTAMP;
ALTER TABLE talent ADD COLUMN IF NOT EXISTS data_source VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_talent_uid ON talent(uid);
CREATE INDEX IF NOT EXISTS idx_talent_sec_uid ON talent(sec_uid);
CREATE INDEX IF NOT EXISTS idx_talent_enrich_status ON talent(enrich_status);
CREATE INDEX IF NOT EXISTS idx_talent_data_source ON talent(data_source);

CREATE TABLE IF NOT EXISTS talent_enrich_task (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    input_value      VARCHAR(500),
    input_type       VARCHAR(50),
    source_type      VARCHAR(50),
    task_status      VARCHAR(50),
    retry_count      INT DEFAULT 0,
    next_retry_time  TIMESTAMP,
    error_msg        TEXT,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_tet_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tet_talent_id ON talent_enrich_task(talent_id);
CREATE INDEX IF NOT EXISTS idx_tet_task_status ON talent_enrich_task(task_status);
CREATE INDEX IF NOT EXISTS idx_tet_next_retry_time ON talent_enrich_task(next_retry_time);

CREATE TABLE IF NOT EXISTS talent_field_source (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    field_name       VARCHAR(64) NOT NULL,
    source_type      VARCHAR(32) NOT NULL,
    source_value     TEXT,
    source_ref_type  VARCHAR(64),
    source_ref_id    VARCHAR(64),
    verified_by      UUID,
    verified_time    TIMESTAMP,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_tfs_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tfs_talent_id ON talent_field_source(talent_id);
CREATE INDEX IF NOT EXISTS idx_tfs_field_name ON talent_field_source(field_name);
CREATE INDEX IF NOT EXISTS idx_tfs_source_type ON talent_field_source(source_type);

CREATE TABLE IF NOT EXISTS talent_auth (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    open_id          VARCHAR(128),
    union_id         VARCHAR(128),
    access_token     TEXT,
    refresh_token    TEXT,
    scope            VARCHAR(500),
    expire_time      TIMESTAMP,
    auth_time        TIMESTAMP,
    status           VARCHAR(30),
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_ta_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ta_talent_id ON talent_auth(talent_id);
CREATE INDEX IF NOT EXISTS idx_ta_open_id ON talent_auth(open_id);
CREATE INDEX IF NOT EXISTS idx_ta_union_id ON talent_auth(union_id);
CREATE INDEX IF NOT EXISTS idx_ta_status ON talent_auth(status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ta_talent_open_id
    ON talent_auth(talent_id, open_id)
    WHERE deleted = 0;

-- 来源: alter-talent-crm-gap-fill.sql
CREATE TABLE IF NOT EXISTS talent_contact (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    contact_type     VARCHAR(30) NOT NULL,
    contact_value    VARCHAR(255) NOT NULL,
    owner_user_id    UUID,
    visible_scope    VARCHAR(30) DEFAULT 'PRIVATE',
    remark           VARCHAR(255),
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_talent_contact_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_talent_contact_talent_id ON talent_contact(talent_id);
CREATE INDEX IF NOT EXISTS idx_talent_contact_owner_user_id ON talent_contact(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_talent_contact_type ON talent_contact(contact_type);
CREATE INDEX IF NOT EXISTS idx_talent_contact_scope ON talent_contact(visible_scope);
CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_contact_unique
    ON talent_contact(talent_id, contact_type, contact_value)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_tag (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_name         VARCHAR(50) NOT NULL,
    tag_type         VARCHAR(30),
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID
);

CREATE INDEX IF NOT EXISTS idx_talent_tag_name ON talent_tag(tag_name);
CREATE INDEX IF NOT EXISTS idx_talent_tag_type ON talent_tag(tag_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_tag_name_type
    ON talent_tag(tag_name, tag_type)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_tag_relation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    tag_id           UUID NOT NULL,
    create_user_id   UUID,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_ttr_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE,
    CONSTRAINT fk_ttr_tag FOREIGN KEY (tag_id) REFERENCES talent_tag(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ttr_talent_id ON talent_tag_relation(talent_id);
CREATE INDEX IF NOT EXISTS idx_ttr_tag_id ON talent_tag_relation(tag_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ttr_unique
    ON talent_tag_relation(talent_id, tag_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_crawl_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID,
    input_value      VARCHAR(500),
    request_url      VARCHAR(1000),
    status           VARCHAR(30),
    error_msg        TEXT,
    raw_data         JSONB,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_tcl_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_tcl_talent_id ON talent_crawl_log(talent_id);
CREATE INDEX IF NOT EXISTS idx_tcl_status ON talent_crawl_log(status);
CREATE INDEX IF NOT EXISTS idx_tcl_create_time ON talent_crawl_log(create_time);

-- 来源: alter-talent-profile-sync.sql
ALTER TABLE talent ADD COLUMN IF NOT EXISTS douyin_account VARCHAR(100);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS talent_uid VARCHAR(100);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS talent_level VARCHAR(20);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS sales_30d BIGINT;
ALTER TABLE talent ADD COLUMN IF NOT EXISTS sync_status VARCHAR(30);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS last_sync_time TIMESTAMP;
ALTER TABLE talent ADD COLUMN IF NOT EXISTS sync_error_code VARCHAR(64);
ALTER TABLE talent ADD COLUMN IF NOT EXISTS sync_error_message TEXT;
ALTER TABLE talent ADD COLUMN IF NOT EXISTS raw_payload JSONB;
ALTER TABLE talent ADD COLUMN IF NOT EXISTS unsupported_fields JSONB;

CREATE INDEX IF NOT EXISTS idx_talent_douyin_account ON talent(douyin_account);
CREATE INDEX IF NOT EXISTS idx_talent_talent_uid ON talent(talent_uid);
CREATE INDEX IF NOT EXISTS idx_talent_sync_status ON talent(sync_status);
CREATE INDEX IF NOT EXISTS idx_talent_last_sync_time ON talent(last_sync_time);

CREATE TABLE IF NOT EXISTS talent_profile_sync_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id           UUID,
    input_value         VARCHAR(500),
    provider_code       VARCHAR(50),
    sync_status         VARCHAR(30),
    fetched_fields      JSONB,
    unsupported_fields  JSONB,
    raw_payload         JSONB,
    error_code          VARCHAR(64),
    error_message       TEXT,
    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         TIMESTAMP,
    deleted             SMALLINT NOT NULL DEFAULT 0,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    CONSTRAINT fk_tpsl_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_tpsl_talent_id ON talent_profile_sync_log(talent_id);
CREATE INDEX IF NOT EXISTS idx_tpsl_provider_code ON talent_profile_sync_log(provider_code);
CREATE INDEX IF NOT EXISTS idx_tpsl_sync_status ON talent_profile_sync_log(sync_status);
CREATE INDEX IF NOT EXISTS idx_tpsl_started_at ON talent_profile_sync_log(started_at);

-- ==== alter-test-existing-volumes-20260504.sql ====
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN DEFAULT FALSE;

ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklist_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_talent_blacklisted
    ON talent(blacklisted);

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS selected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS selected_by UUID;

CREATE INDEX IF NOT EXISTS idx_product_operation_state_selected_to_library
    ON product_operation_state(selected_to_library);

ALTER TABLE pick_source_mapping
    ALTER COLUMN pick_extra TYPE VARCHAR(128);

-- ==== alter-merchant-ownership.sql ====
-- ?????????? owner_id / owner_dept_id ?????? UUID ??
ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS owner_id UUID,
    ADD COLUMN IF NOT EXISTS owner_dept_id UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_id TYPE UUID
    USING NULLIF(TRIM(owner_id::text), '')::UUID;

ALTER TABLE merchant
    ALTER COLUMN owner_dept_id TYPE UUID
    USING NULLIF(TRIM(owner_dept_id::text), '')::UUID;

-- ==== alter-product-test-columns.sql ====
-- ????????? test ?? schema ???????????
-- ?????init-db.sql
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS external_product_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS test_tag VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_product_external_id
    ON product(external_product_id)
    WHERE external_product_id IS NOT NULL;

-- ==== alter-product-extended-columns.sql ====
-- ????????? test ?? schema ???????????
-- ?????init-db.sql
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS quality_tag VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS creator_ip VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version_tag INTEGER DEFAULT 0;

-- ==== alter-product-biz-status.sql ====
ALTER TABLE product_operation_state
ADD COLUMN IF NOT EXISTS biz_status VARCHAR(64);

-- ==== alter-product-following.sql + alter-talent-follow-record-crm-columns.sql ====
CREATE TABLE IF NOT EXISTS talent_follow_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       VARCHAR(64) NOT NULL,
    activity_id      VARCHAR(64),
    talent_id        UUID,
    talent_name      VARCHAR(255),
    follow_status    VARCHAR(64),
    content          TEXT,
    next_follow_time TIMESTAMP,
    operator_id      UUID,
    operator_name    VARCHAR(255),
    user_id          UUID,
    follow_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    unfollow_time    TIMESTAMP,
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    deleted          SMALLINT NOT NULL DEFAULT 0
);

-- Existing volumes may have created talent_follow_record from
-- alter-product-following.sql before these CRM columns existed.
ALTER TABLE talent_follow_record
    ADD COLUMN IF NOT EXISTS user_id UUID,
    ADD COLUMN IF NOT EXISTS follow_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS unfollow_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tfr_product_id ON talent_follow_record(product_id);
CREATE INDEX IF NOT EXISTS idx_tfr_talent_id  ON talent_follow_record(talent_id);
CREATE INDEX IF NOT EXISTS idx_tfr_user_id    ON talent_follow_record(user_id);
CREATE INDEX IF NOT EXISTS idx_talent_follow_record_activity_product
    ON talent_follow_record(activity_id, product_id, create_time DESC);

ALTER TABLE talent_follow_record
    ADD COLUMN IF NOT EXISTS activity_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS talent_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS follow_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS next_follow_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS operator_id UUID,
    ADD COLUMN IF NOT EXISTS operator_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS create_by UUID,
    ADD COLUMN IF NOT EXISTS update_by UUID,
    ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE talent_follow_record
    ALTER COLUMN talent_id DROP NOT NULL;

-- ==== alter-product-main-chain.sql ====
-- ?????????P0?
-- ?????init-db.sql
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS assignee_id   UUID,
    ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS audit_status VARCHAR(32) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS audit_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_pos_assignee ON product_operation_state(assignee_id)
    WHERE assignee_id IS NOT NULL;

-- ==== alter-sys-dept.sql ====
-- =============================================
-- sys_dept ????????????
-- =============================================
ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS sort_order  INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dept_type   VARCHAR(32) DEFAULT 'BUSINESS',
    ADD COLUMN IF NOT EXISTS outer_dept_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sys_dept_type ON sys_dept(dept_type);

-- ==== alter-menu-permission-model.sql ====
-- ============================================================
-- Gap 1: ??/?????? � sys_menu + sys_role_menu
-- ============================================================
ALTER TABLE sys_menu
    ADD COLUMN IF NOT EXISTS menu_type    VARCHAR(16) DEFAULT 'MENU',
    ADD COLUMN IF NOT EXISTS permission   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_frame     BOOLEAN    DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache        BOOLEAN    DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS visible       BOOLEAN    DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS status        VARCHAR(16) DEFAULT 'ENABLED',
    ADD COLUMN IF NOT EXISTS perms        VARCHAR(100);

ALTER TABLE sys_role_menu
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ==== alter-sample-shipper-code.sql ====
ALTER TABLE sample_request
ADD COLUMN IF NOT EXISTS shipper_code VARCHAR(32);

-- ==== alter-talent-profile-sync.sql ====
-- =============================================
-- ????????????????????
-- ?????? colonel_saas ??????
-- =============================================
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS follower_count   INTEGER,
    ADD COLUMN IF NOT EXISTS avg_views        NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS last_synced_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sync_error       VARCHAR(512);

ALTER TABLE talent_profile_sync_log
    ADD COLUMN IF NOT EXISTS request_params TEXT,
    ADD COLUMN IF NOT EXISTS response_code VARCHAR(32);

-- ==== alter-talent-enrich.sql ====
-- =============================================
-- ??????????????????
-- ?????? colonel_saas ???????????
-- =============================================
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS birthday          DATE,
    ADD COLUMN IF NOT EXISTS cert_no           VARCHAR(64),
    ADD COLUMN IF NOT EXISTS contact_name      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS contact_phone     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS live_room_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS live_fans_count   INTEGER,
    ADD COLUMN IF NOT EXISTS last_live_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS categories        TEXT[],
    ADD COLUMN IF NOT EXISTS tags              TEXT[],
    ADD COLUMN IF NOT EXISTS en_name           VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_platform   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_url        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS fail_reason       VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cert_image_url    VARCHAR(512),
    ADD COLUMN IF NOT EXISTS avatar_image_url  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS data_source      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS external_id      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS data_version     INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS verified_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verified_by      UUID,
    ADD COLUMN IF NOT EXISTS source_fields    TEXT,
    ADD COLUMN IF NOT EXISTS latest_synced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_talent_cert_no       ON talent(cert_no)      WHERE cert_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_talent_contact_phone ON talent(contact_phone) WHERE contact_phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_talent_external_id   ON talent(external_id)  WHERE external_id IS NOT NULL;

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS claim_channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS expire_time   TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_tc_claim_channel ON talent_claim(claim_channel)
    WHERE claim_channel IS NOT NULL;

CREATE TABLE IF NOT EXISTS talent_protect (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id       UUID NOT NULL,
    talent_uid      VARCHAR(50) NOT NULL,
    user_id         UUID,
    dept_id         UUID,
    protect_level   VARCHAR(16) DEFAULT 'NORMAL',
    expire_time     TIMESTAMP,
    status          SMALLINT NOT NULL DEFAULT 1,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    version         INTEGER NOT NULL DEFAULT 0,
    remark          TEXT,
    CONSTRAINT fk_tp_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);

ALTER TABLE talent_protect
    ADD COLUMN IF NOT EXISTS protect_level VARCHAR(16) DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS expire_time   TIMESTAMP;

-- ==== alter-talent-crm-gap-fill.sql ====
-- =============================================
-- ??????? CRM ???????
-- ?????init-db.sql?alter-talent-enrich.sql
-- =============================================
ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS current_cycle_start DATE,
    ADD COLUMN IF NOT EXISTS current_cycle_end   DATE,
    ADD COLUMN IF NOT EXISTS last_active_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS total_order_count   INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_gmv          NUMERIC(14,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS claim_source       VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tc_user_talent ON talent_claim(user_id, talent_id)
    WHERE status = 1;

-- ==== alter-order-talent-id.sql ====
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS talent_id UUID;

-- ==== alter-order-pick-source-length.sql ====
ALTER TABLE colonelsettlement_order
    ALTER COLUMN pick_source TYPE VARCHAR(128);

-- ==== alter-order-attribution-mvp.sql ====
ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_status VARCHAR(32) DEFAULT 'UNATTRIBUTED';

ALTER TABLE colonelsettlement_order
ADD COLUMN IF NOT EXISTS attribution_remark VARCHAR(255);

-- ==== alter-order-attribution-mvp-v2.sql ====
-- =============================================
-- ??????? MVP ????
-- =============================================
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS attribution_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_attempt_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS attempt_count     INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attribution_amt   NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS service_fee_amt    NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS settle_status     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS settle_time        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS colonelsettlement_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS colonel_nickname  VARCHAR(128),
    ADD COLUMN IF NOT EXISTS product_title     VARCHAR(256),
    ADD COLUMN IF NOT EXISTS product_pic       VARCHAR(512),
    ADD COLUMN IF NOT EXISTS order_source     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS activity_title   VARCHAR(256),
    ADD COLUMN IF NOT EXISTS order_type       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS origin           VARCHAR(64),
    ADD COLUMN IF NOT EXISTS attribution_rule VARCHAR(64),
    ADD COLUMN IF NOT EXISTS colonelsettlement_time TIMESTAMP;

ALTER TABLE colonelsettlement_order
    ALTER COLUMN attribution_status SET DEFAULT 'UNATTRIBUTED',
    ALTER COLUMN settle_status      SET DEFAULT 'UNPAID';

CREATE INDEX IF NOT EXISTS idx_cso_attribution_status ON colonelsettlement_order(attribution_status);
CREATE INDEX IF NOT EXISTS idx_cso_settle_status      ON colonelsettlement_order(settle_status);
CREATE INDEX IF NOT EXISTS idx_cso_order_type         ON colonelsettlement_order(order_type);

-- ==== alter-pick-source-mapping-colonel-buyin-id.sql ====
-- ??????????? 19 ? colonel_buyin_id????? 8-10 ? short_id?
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS colonel_buyin_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_psm_colonel_id ON pick_source_mapping(colonel_buyin_id)
    WHERE colonel_buyin_id IS NOT NULL;

-- ==== alter-pick-source-mapping-colonel-name.sql ====
-- ColonelPartnerSyncService.loadFromPickSourceMapping 查询团长名称。
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS colonel_name VARCHAR(256);

-- ==== alter-pick-source-mapping-duplicate-pick-source.sql ====
-- ????????????? pick_source ???????????????
-- ???? pick_source ????????????????????????
ALTER TABLE pick_source_mapping
    DROP CONSTRAINT IF EXISTS uk_pick_source;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_pick_source_product'
          AND conrelid = 'pick_source_mapping'::regclass
    ) THEN
        ALTER TABLE pick_source_mapping
            ADD CONSTRAINT uk_pick_source_product
                UNIQUE (pick_source, product_id);
    END IF;
END $$;

-- ==== alter-pick-source-mapping-native-source-type.sql ====
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'PICK_SOURCE';

ALTER TABLE pick_source_mapping
    ALTER COLUMN source_type SET DEFAULT 'PICK_SOURCE';

CREATE INDEX IF NOT EXISTS idx_psm_source_type ON pick_source_mapping(source_type);

-- ==== alter-pick-source-mapping-pick-extra-length.sql ====
ALTER TABLE pick_source_mapping
    ALTER COLUMN pick_extra TYPE VARCHAR(128);

-- ==== alter-partitioned-order-log-pk-default.sql ====
-- =============================================
-- ?????? / ???? � id ??????????????
-- =============================================
ALTER TABLE operation_log
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE colonelsettlement_order
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

CREATE OR REPLACE FUNCTION ensure_partition_pk()
RETURNS void AS $$
DECLARE
    partition_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO partition_count
    FROM pg_tables
    WHERE tablename LIKE 'colonelsettlement_order_%';
END;
$$ LANGUAGE plpgsql;

SELECT ensure_partition_pk();
DROP FUNCTION IF EXISTS ensure_partition_pk();

-- ==== alter-cso-sample-attribution-indexes.sql ====
-- =============================================
-- M6/M7/M8 ????????? � ????
-- =============================================
CREATE INDEX IF NOT EXISTS idx_cso_user_create
    ON colonelsettlement_order(user_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_cso_dept_create
    ON colonelsettlement_order(dept_id, create_time DESC)
    WHERE dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_colonel_id
    ON colonelsettlement_order(colonel_buyin_id)
    WHERE colonel_buyin_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_talent_id
    ON colonelsettlement_order(talent_id)
    WHERE talent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_pick_source
    ON colonelsettlement_order(pick_source)
    WHERE pick_source IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_attribution
    ON colonelsettlement_order(attribution_status, create_time DESC)
    WHERE attribution_status = 'ATTRIBUTED';

-- ==== alter-db-performance-contract-20260521.sql ====
-- Schema-only guardrails (FIN-01 / FK-01 / STATUS-01). Backfill/cleanup stays in separate scripts.

CREATE INDEX IF NOT EXISTS idx_cso_deleted
    ON colonelsettlement_order (deleted);
CREATE INDEX IF NOT EXISTS idx_cso_user_create_time
    ON colonelsettlement_order (user_id, create_time DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_cso_dept_create_time
    ON colonelsettlement_order (dept_id, create_time DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_sr_channel_user_status
    ON sample_request (channel_user_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pos_product_biz_status
    ON product_operation_state (product_id, biz_status)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pos_product_audit_status
    ON product_operation_state (product_id, audit_status)
    WHERE deleted = 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_data_scope'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_data_scope
            CHECK (data_scope IN (1, 2, 3)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sys_role_status'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_status
            CHECK (status IN (0, 1)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_sample_request_status'
    ) THEN
        ALTER TABLE sample_request
            ADD CONSTRAINT ck_sample_request_status
            CHECK (status BETWEEN 1 AND 8) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cso_order_type'
    ) THEN
        ALTER TABLE colonelsettlement_order
            ADD CONSTRAINT ck_cso_order_type
            CHECK (order_type IS NULL OR order_type >= 0) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_talent_non_negative_financials'
    ) THEN
        ALTER TABLE exclusive_talent
            ADD CONSTRAINT ck_exclusive_talent_non_negative_financials
            CHECK (
                service_fee >= 0
                AND channel_total_fee >= 0
                AND service_fee_ratio >= 0
                AND monthly_samples >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_merchant_non_negative_financials'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT ck_exclusive_merchant_non_negative_financials
            CHECK (
                service_fee >= 0
                AND business_total_fee >= 0
                AND service_fee_ratio >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_commission_settlement_non_negative_financials'
    ) THEN
        ALTER TABLE commission_settlement
            ADD CONSTRAINT ck_commission_settlement_non_negative_financials
            CHECK (
                order_count >= 0
                AND total_order_amount >= 0
                AND commission_amount >= 0
                AND tech_service_fee >= 0
                AND net_commission >= 0
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cso_non_negative_financials'
    ) THEN
        ALTER TABLE colonelsettlement_order
            ADD CONSTRAINT ck_cso_non_negative_financials
            CHECK (
                (order_amount IS NULL OR order_amount >= 0)
                AND (actual_amount IS NULL OR actual_amount >= 0)
                AND (settle_colonel_commission IS NULL OR settle_colonel_commission >= 0)
                AND (settle_colonel_tech_service_fee IS NULL OR settle_colonel_tech_service_fee >= 0)
                AND (settle_second_colonel_commission IS NULL OR settle_second_colonel_commission >= 0)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_talent_status'
    ) THEN
        ALTER TABLE exclusive_talent
            ADD CONSTRAINT ck_exclusive_talent_status
            CHECK (status IN (0, 1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_exclusive_merchant_status'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT ck_exclusive_merchant_status
            CHECK (status IN (0, 1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_em_merchant'
    ) THEN
        ALTER TABLE exclusive_merchant
            ADD CONSTRAINT fk_em_merchant
            FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_product_operation_state_biz_status'
    ) THEN
        ALTER TABLE product_operation_state
            ADD CONSTRAINT ck_product_operation_state_biz_status
            CHECK (
                biz_status IS NULL
                OR biz_status IN (
                    'PENDING_AUDIT',
                    'APPROVED',
                    'REJECTED',
                    'BOUND',
                    'ASSIGNED',
                    'LINKED',
                    'FOLLOWING',
                    'SYNCED'
                )
            ) NOT VALID;
    END IF;
END $$;

-- ==== alter-optimistic-lock-core-20260522.sql ====
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN talent.version IS '??????';
COMMENT ON COLUMN product_operation_state.version IS '??????';
COMMENT ON COLUMN pick_source_mapping.version IS '??????';
COMMENT ON COLUMN talent_claim.version IS '??????';
COMMENT ON COLUMN sample_request.version IS '??????';
COMMENT ON COLUMN merchant.version IS '??????';
COMMENT ON COLUMN colonelsettlement_order.version IS '??????';

COMMENT ON TABLE colonelsettlement_order IS '??????????? by create_time?';
COMMENT ON COLUMN colonelsettlement_order.order_type     IS 'MAIN=????????SETTLEMENT=????????';
COMMENT ON COLUMN colonelsettlement_order.colonel_buyin_id IS '??19?????????????';
COMMENT ON COLUMN colonelsettlement_order.pick_source     IS '???????????? colonel_buyin_id';

-- ==== alter-v1-gaps-20260522.sql ====
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pinned_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pinned_by UUID;

CREATE INDEX IF NOT EXISTS idx_pos_pinned_by_until
    ON product_operation_state (pinned_by, pinned_until)
    WHERE pinned_until IS NOT NULL;

ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS talent_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS tag_updated_by UUID,
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_recipient_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS shipping_recipient_address VARCHAR(512);

ALTER TABLE talent_claim
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(512);

CREATE TABLE IF NOT EXISTS dashboard_performance_daily (
    stat_date DATE NOT NULL PRIMARY KEY,
    order_count BIGINT NOT NULL DEFAULT 0,
    order_amount BIGINT NOT NULL DEFAULT 0,
    service_fee_net BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'promotion.copy_brief_template',
    '【{productName}】\n佣金率：{commissionRate}\n短链：{shortLink}',
    'text',
    'promotion',
    '复制讲解模板，占位符：{productName} {commissionRate} {shortLink} {pickSource}',
    1
)
ON CONFLICT (config_key) DO NOTHING;

-- colonel_leader merged into biz_leader — see alter-role-code-merge-colonel-leader.sql

-- ==== alter-dual-track-performance-20260522.sql ====
ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS settle_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimate_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimate_tech_service_fee BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_tech_service_fee BIGINT NOT NULL DEFAULT 0;

UPDATE colonelsettlement_order
SET settle_amount = COALESCE(NULLIF(actual_amount, 0), order_amount, 0),
    estimate_service_fee = COALESCE(NULLIF(settle_colonel_commission, 0), 0),
    effective_service_fee = COALESCE(settle_colonel_commission, 0),
    estimate_tech_service_fee = COALESCE(settle_colonel_tech_service_fee, 0),
    effective_tech_service_fee = COALESCE(settle_colonel_tech_service_fee, 0)
WHERE estimate_service_fee = 0
  AND effective_service_fee = 0;

ALTER TABLE colonelsettlement_order
    ADD COLUMN IF NOT EXISTS estimate_service_fee_expense BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_service_fee_expense BIGINT NOT NULL DEFAULT 0;

ALTER TABLE performance_records
    ADD COLUMN IF NOT EXISTS estimate_service_fee_expense BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_service_fee_expense BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS performance_records (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                        VARCHAR(50) NOT NULL,
    order_row_id                    UUID,
    default_channel_user_id         UUID,
    default_recruiter_user_id       UUID,
    final_channel_user_id           UUID,
    final_recruiter_user_id         UUID,
    channel_attribution             VARCHAR(32),
    recruiter_attribution           VARCHAR(32),
    talent_id                       UUID,
    partner_id                      BIGINT,
    product_id                      VARCHAR(50),
    activity_id                     VARCHAR(50),
    pay_amount                      BIGINT NOT NULL DEFAULT 0,
    settle_amount                   BIGINT NOT NULL DEFAULT 0,
    estimate_service_fee            BIGINT NOT NULL DEFAULT 0,
    effective_service_fee           BIGINT NOT NULL DEFAULT 0,
    estimate_tech_service_fee       BIGINT NOT NULL DEFAULT 0,
    effective_tech_service_fee      BIGINT NOT NULL DEFAULT 0,
    estimate_service_profit         BIGINT NOT NULL DEFAULT 0,
    effective_service_profit        BIGINT NOT NULL DEFAULT 0,
    estimate_recruiter_commission   BIGINT NOT NULL DEFAULT 0,
    effective_recruiter_commission  BIGINT NOT NULL DEFAULT 0,
    estimate_channel_commission     BIGINT NOT NULL DEFAULT 0,
    effective_channel_commission    BIGINT NOT NULL DEFAULT 0,
    estimate_gross_profit           BIGINT NOT NULL DEFAULT 0,
    effective_gross_profit          BIGINT NOT NULL DEFAULT 0,
    recruiter_commission_rate       NUMERIC(8, 4),
    channel_commission_rate         NUMERIC(8, 4),
    order_status                    SMALLINT,
    settle_time                     TIMESTAMP,
    order_create_time               TIMESTAMP,
    is_valid                        BOOLEAN NOT NULL DEFAULT TRUE,
    is_reversed                     BOOLEAN NOT NULL DEFAULT FALSE,
    calculation_version             INTEGER NOT NULL DEFAULT 1,
    calculated_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_performance_records_order_id UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_performance_records_settle_time
    ON performance_records (settle_time DESC);

-- ==== alter-config-change-log-20260522.sql ====
CREATE TABLE IF NOT EXISTS system_config_change_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id     UUID,
    config_key    VARCHAR(100) NOT NULL,
    change_action VARCHAR(20)  NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    source        VARCHAR(50)  NOT NULL,
    operator_id   UUID,
    changed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sccl_config_key_changed
    ON system_config_change_log (config_key, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_sccl_operator_changed
    ON system_config_change_log (operator_id, changed_at DESC);

COMMENT ON TABLE system_config_change_log IS '???????????????????????????';

-- ==== alter-v2-config-20260523.sql ====
CREATE TABLE IF NOT EXISTS commissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_type VARCHAR(32) NOT NULL,
    dimension_id VARCHAR(128),
    commission_type VARCHAR(32) NOT NULL,
    ratio NUMERIC(10, 4) NOT NULL,
    effective_start TIMESTAMP,
    effective_end TIMESTAMP,
    status SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    CONSTRAINT chk_commissions_dimension_type
        CHECK (dimension_type IN ('global', 'activity', 'product', 'user')),
    CONSTRAINT chk_commissions_commission_type
        CHECK (commission_type IN ('recruiter', 'channel')),
    CONSTRAINT chk_commissions_ratio_range
        CHECK (ratio >= 0 AND ratio <= 1)
);

CREATE INDEX IF NOT EXISTS idx_commissions_dimension_lookup
    ON commissions (dimension_type, dimension_id, commission_type, status, deleted);

CREATE INDEX IF NOT EXISTS idx_commissions_effective_window
    ON commissions (effective_start, effective_end);

ALTER TABLE commissions
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS create_by UUID,
    ADD COLUMN IF NOT EXISTS update_by UUID;

ALTER TABLE commissions ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

COMMENT ON TABLE commissions IS 'V2 ????????global/activity/product/user � recruiter/channel?';

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'talent.preset_tags',
    '["美妆","高转化","服饰","食品","母婴","家居","数码","本地生活"]',
    'json',
    'talent',
    '达人预设标签库',
    1
)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES (
    'promotion.pick_extra_rule',
    '{"format":"channel_{channel_code}","encode":"none"}',
    'json',
    'promotion',
    'pick_extra 生成规则',
    1
)
ON CONFLICT (config_key) DO NOTHING;

-- ==== alter-domain-event-config-20260523.sql ====
CREATE TABLE IF NOT EXISTS domain_event_outbox (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128),
    event_version   INT          NOT NULL DEFAULT 1,
    payload         JSONB        NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    occurred_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    created_by      VARCHAR(64),
    trace_id        VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_status_time
    ON domain_event_outbox (status, occurred_at);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_type_time
    ON domain_event_outbox (event_type, occurred_at);

CREATE TABLE IF NOT EXISTS domain_event_consume_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    error_message TEXT,
    consumed_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_domain_event_consume UNIQUE (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_consume_event
    ON domain_event_consume_log (event_id);

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS config_version INT NOT NULL DEFAULT 1;

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE system_config
    ADD COLUMN IF NOT EXISTS visible_in_rule_center BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS event_id UUID;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS change_reason TEXT;

ALTER TABLE system_config_change_log
    ADD COLUMN IF NOT EXISTS config_version INT;

INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status, visible_in_rule_center)
VALUES
    ('auth.login_max_failures', '5', 'int', 'security', '登录失败锁定次数', 1, TRUE),
    ('auth.login_lock_minutes', '15', 'int', 'security', '登录锁定时长（分钟）', 1, TRUE),
    ('promotion.copy_brief_template',
     $$【抖音】{product_name}
【佣金率】{commission_rate}
【推广链接】{promotion_link}$$,
     'string', 'promotion', '复制讲解模板', 1, TRUE)
ON CONFLICT (config_key) DO NOTHING;

-- ==== alter-user-domain-v1-acceptance-20260523.sql ====
-- ??? V1 ??????????? + sys_dept ????
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS force_password_change BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS leader_user_id UUID;

UPDATE sys_dept SET dept_type = 'recruiter_group'
WHERE dept_code = 'BIZ' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'channel_group'
WHERE dept_code = 'CHANNEL' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

UPDATE sys_dept SET dept_type = 'ops_group'
WHERE dept_code = 'OPS' AND (dept_type IS NULL OR dept_type IN ('BUSINESS', 'department'));

ALTER TABLE sys_dept ALTER COLUMN dept_type SET DEFAULT 'department';

-- ==== alter-product-display-rule-20260523.sql ====
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS display_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS first_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS display_rule_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_pos_product_display_status
    ON product_operation_state (product_id, display_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pos_displaying_library
    ON product_operation_state (display_status, selected_to_library)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';

-- ==== alter-colonel-partner-20260523.sql ====
CREATE TABLE IF NOT EXISTS colonel_partner (
    id UUID PRIMARY KEY,
    colonel_buyin_id VARCHAR(64) NOT NULL,
    colonel_name VARCHAR(256),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    source_updated_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by UUID,
    update_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_colonel_partner_buyin_id
    ON colonel_partner (colonel_buyin_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_colonel_partner_name
    ON colonel_partner (colonel_name)
    WHERE deleted = 0;

-- ????????????/?????Outbox ????????????????�?
-- 1. ??????
CREATE TABLE IF NOT EXISTS product_display_rule_version (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_no   INTEGER      NOT NULL UNIQUE,
    config_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO product_display_rule_version (version_no, config_json, enabled, created_by)
VALUES (
    3,
    '{"protectionMonthsDefault":3,"sortOrder":["FORCE","ADS","COMMISSION","SERVICE_FEE","SHELF_TIME","RELATION_ID"],"advantageOverride":true}'::jsonb,
    TRUE,
    'system'
)
ON CONFLICT (version_no) DO NOTHING;

-- 2. ??????
CREATE TABLE IF NOT EXISTS product_display_audit_log (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id             VARCHAR(64)  NOT NULL,
    old_relation_id        UUID,
    new_relation_id        UUID,
    candidate_relation_ids JSONB,
    action_type            VARCHAR(64)  NOT NULL,
    selected_reason        VARCHAR(128),
    hidden_reason          VARCHAR(128),
    rule_version           INTEGER      NOT NULL,
    operator_type          VARCHAR(32)  NOT NULL,
    operator_id            VARCHAR(64),
    detail_json            JSONB,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_display_audit_product_time
    ON product_display_audit_log (product_id, created_at DESC);

-- 3. product_operation_state ??
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS display_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS force_display BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS force_display_by UUID,
    ADD COLUMN IF NOT EXISTS force_display_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS force_display_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS display_priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS manual_disabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pos_one_displaying_per_product
    ON product_operation_state (product_id)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';

-- 4. domain_event_outbox ????????????
ALTER TABLE domain_event_outbox
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS headers JSONB,
    ADD COLUMN IF NOT EXISTS max_retry INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_event_outbox_event_key
    ON domain_event_outbox (event_key)
    WHERE event_key IS NOT NULL;

-- 5. colonel_partner ??
ALTER TABLE colonel_partner
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS create_by UUID,
    ADD COLUMN IF NOT EXISTS update_by UUID,
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS contact_wechat VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_remark TEXT,
    ADD COLUMN IF NOT EXISTS source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS manual_contact_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS manual_contact_updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS raw_payload JSONB;

-- 6. sample_request ??????
ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS apply_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_apply_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS external_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_error_message TEXT,
    ADD COLUMN IF NOT EXISTS external_raw_payload JSONB;

CREATE INDEX IF NOT EXISTS idx_sample_request_apply_source
    ON sample_request (apply_source)
    WHERE deleted = 0;

-- ==== alter-sample-logistics-20260523.sql ====
ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS logistics_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_status_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS logistics_last_query_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_error TEXT,
    ADD COLUMN IF NOT EXISTS logistics_raw_payload JSONB,
    ADD COLUMN IF NOT EXISTS signed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS external_last_sync_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_sample_request_logistics_sync
    ON sample_request (status, logistics_last_query_at)
    WHERE deleted = 0 AND tracking_no IS NOT NULL AND tracking_no <> '';

CREATE TABLE IF NOT EXISTS sample_logistics_trace (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_request_id   UUID NOT NULL,
    tracking_no         VARCHAR(100),
    logistics_company   VARCHAR(50),
    status_code         VARCHAR(32),
    status_name         VARCHAR(64),
    trace_time          TIMESTAMP,
    trace_content       TEXT,
    raw_payload         JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sample_logistics_trace_request
    ON sample_logistics_trace (sample_request_id, trace_time DESC);

-- 快递100订阅推送字段：仍挂在寄样履约链路，不新增独立物流业务域
ALTER TABLE sample_request
    ADD COLUMN IF NOT EXISTS logistics_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_subscribe_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_subscribed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_subscribe_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_last_callback_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS logistics_callback_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS logistics_callback_message TEXT,
    ADD COLUMN IF NOT EXISTS logistics_exception_reason TEXT;

ALTER TABLE sample_logistics_trace
    ADD COLUMN IF NOT EXISTS node_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS location VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_logistics_trace_request_node
    ON sample_logistics_trace (sample_request_id, node_hash)
    WHERE node_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sample_request_logistics_callback
    ON sample_request (status, logistics_last_callback_at)
    WHERE deleted = 0 AND tracking_no IS NOT NULL AND tracking_no <> '';

-- Performance V1 query closure indexes (2026-05-23)
CREATE INDEX IF NOT EXISTS idx_performance_records_order_create_time
    ON performance_records (order_create_time DESC);

CREATE INDEX IF NOT EXISTS idx_performance_records_calculated_at
    ON performance_records (calculated_at DESC);

CREATE INDEX IF NOT EXISTS idx_performance_records_activity
    ON performance_records (activity_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_product
    ON performance_records (product_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_partner
    ON performance_records (partner_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_talent
    ON performance_records (talent_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_order_status
    ON performance_records (order_status);

-- ============================================================
-- order_sync_dedup_claim
-- ============================================================
CREATE TABLE IF NOT EXISTS order_sync_dedup_claim (
    order_id      VARCHAR(128) PRIMARY KEY,
    order_row_id  UUID,
    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_sync_dedup_claim_row_id
    ON order_sync_dedup_claim(order_row_id);

-- ============================================================
-- ============================================================
-- 管理员密码不应由 migrate-all.sql 重置。
-- ADMIN_PASSWORD 仅用于 PostgreSQL volume 首次初始化时的 init-db.sql，
-- 后续迁移不得覆盖已有用户的密码，否则会导致已修改的密码在迁移后被意外重置。
-- 如需强制重置管理员密码，应通过后台管理界面或专门的 SQL 脚本操作。
-- ============================================================
\i alter-colonel-activity-recruiter-assignment.sql
\i alter-role-code-merge-colonel-leader.sql
\i alter-colonel-activity-product-state-split.sql
\i alter-colonelsettlement-order-dual-track.sql
\i alter-ops-staff-data-scope-20260520.sql
\i alter-order-pay-time.sql
\i alter-sys-dept-uuid-canonical-20260530.sql
\i alter-talent-claim-shipping-address.sql
\i create-colonel-order-settlement.sql
\i alter-product-activity-backfill-state-20260615.sql
\i alter-product-library-query-performance-20260625.sql
\i migrate-sys-dept-dept-type.sql
\i alter-sample-default-standard-disable-20260716.sql
\i alter-authorization-foundation-20260713.sql
\i alter-role-aware-promotion-link-attribution-20260716.sql
\i alter-order-default-attribution-dimensions-20260716.sql
\i alter-performance-final-attribution-20260716.sql
