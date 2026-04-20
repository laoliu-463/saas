-- =============================================
-- 抖音团长 SaaS 系统 - 数据库初始化脚本 V1.3
-- PostgreSQL 15+
-- 幂等设计：IF NOT EXISTS / ON CONFLICT DO NOTHING
-- =============================================

-- 扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================
-- 1. 用户与权限
-- =============================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    real_name     VARCHAR(100),
    phone         VARCHAR(20),
    email         VARCHAR(100),
    dept_id       UUID,
    channel_code  VARCHAR(16)  NOT NULL UNIQUE,     -- [V1.2] 渠道短码，用于 pick_extra 生成（channel_{code} ≤20字符）
    status        SMALLINT    NOT NULL DEFAULT 1,  -- 1=启用, 0=禁用
    deleted       SMALLINT    NOT NULL DEFAULT 0,
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    last_login_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_user_dept_id  ON sys_user(dept_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_channel_code ON sys_user(channel_code);
CREATE INDEX IF NOT EXISTS idx_sys_user_status   ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_sys_user_deleted  ON sys_user(deleted);

CREATE TABLE IF NOT EXISTS sys_role (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code     VARCHAR(50)  NOT NULL UNIQUE,
    role_name     VARCHAR(100) NOT NULL,
    data_scope    SMALLINT    NOT NULL DEFAULT 1,  -- 1=仅自己, 2=本组, 3=全部
    permissions   JSONB,                          -- [V1.2] 操作权限配置（增/删/改/查/导出）
    menu_config   JSONB,                          -- [V1.2] 可见菜单配置
    status        SMALLINT    NOT NULL DEFAULT 1,
    deleted       SMALLINT    NOT NULL DEFAULT 0,
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    remark        VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_sys_role_code ON sys_role(role_code);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    role_id    UUID NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_ur_user_id ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_ur_role_id ON sys_user_role(role_id);

CREATE TABLE IF NOT EXISTS sys_department (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     UUID,
    dept_name     VARCHAR(100) NOT NULL,
    dept_type     VARCHAR(20) NOT NULL,            -- business=招商组, channel=渠道组
    leader_user_id UUID,
    sort_order    INT       DEFAULT 0,
    deleted       SMALLINT  NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID
);
CREATE INDEX IF NOT EXISTS idx_dept_parent ON sys_department(parent_id);
CREATE INDEX IF NOT EXISTS idx_dept_type   ON sys_department(dept_type);

-- =============================================
-- 2. 抖音 Token
-- =============================================

CREATE TABLE IF NOT EXISTS douyin_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id          VARCHAR(100),                          -- [V1.3] 抖店应用ID
    shop_id         BIGINT       NOT NULL UNIQUE,
    shop_name       VARCHAR(200),
    authority_id    VARCHAR(100),
    auth_subject_type VARCHAR(50),
    token_type      SMALLINT     NOT NULL DEFAULT 0,  -- 0=主账号, 1=子账号
    access_token    TEXT,
    refresh_token   TEXT,
    expires_in      BIGINT,
    token_expire_at TIMESTAMP,
    refresh_expire_at TIMESTAMP,
    encrypt_operator VARCHAR(200),
    operator_name   VARCHAR(100),
    toutiao_id      VARCHAR(100),
    status          SMALLINT    NOT NULL DEFAULT 1,  -- 1=有效, 0=失效
    deleted         SMALLINT    NOT NULL DEFAULT 0,
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    extra_data      JSONB                          -- [V1.2] 存储scope/shop_biz_type等API扩展字段
);
CREATE INDEX IF NOT EXISTS idx_token_shop_id    ON douyin_token(shop_id);
CREATE INDEX IF NOT EXISTS idx_token_expire_at  ON douyin_token(token_expire_at);
CREATE INDEX IF NOT EXISTS idx_token_status     ON douyin_token(status);
CREATE INDEX IF NOT EXISTS idx_token_deleted    ON douyin_token(deleted);

-- =============================================
-- 3. 活动与商品
-- =============================================

CREATE TABLE IF NOT EXISTS colonel_activity (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id        VARCHAR(50)  NOT NULL UNIQUE,
    activity_name      VARCHAR(500),
    activity_type      VARCHAR(20),
    shop_id            BIGINT,
    shop_name          VARCHAR(200),
    colonel_buyin_id   BIGINT,                          -- [V1.2] 团长百应ID（匹配订单归属）
    commission_rate    NUMERIC(5,4),                    -- [V1.2] 最低佣金率（活动门槛）
    service_rate       NUMERIC(5,4),                    -- [V1.2] 最低服务费率（活动门槛）
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    status             VARCHAR(20),
    months_of_protection INT,
    last_sync_at       TIMESTAMP,                       -- [V1.2] 最近同步时间
    deleted            SMALLINT    NOT NULL DEFAULT 0,
    create_time        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          UUID,
    update_by          UUID,
    extra_data         JSONB
);
CREATE INDEX IF NOT EXISTS idx_activity_shop_id       ON colonel_activity(shop_id);
CREATE INDEX IF NOT EXISTS idx_activity_colonel_id    ON colonel_activity(colonel_buyin_id);
CREATE INDEX IF NOT EXISTS idx_activity_status        ON colonel_activity(status);
CREATE INDEX IF NOT EXISTS idx_activity_end_time      ON colonel_activity(end_time);
CREATE INDEX IF NOT EXISTS idx_activity_deleted       ON colonel_activity(deleted);

CREATE TABLE IF NOT EXISTS product (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         VARCHAR(50)  NOT NULL UNIQUE,
    outer_product_id   VARCHAR(50),
    name               VARCHAR(500) NOT NULL,
    description        TEXT,
    market_price       BIGINT,
    discount_price     BIGINT,
    cover              VARCHAR(1000),
    detail_url         VARCHAR(1000),
    first_cid          BIGINT,
    second_cid         BIGINT,
    third_cid          BIGINT,
    fourth_cid         BIGINT,
    category_detail    JSONB,
    pics               JSONB,
    spec_prices        JSONB,
    cos_ratio           NUMERIC(5,2),                        -- [V1.3] 佣金比例
    cos_fee             BIGINT,                              -- [V1.3] 佣金金额（分）
    service_ratio       NUMERIC(5,2),                        -- [V1.3] 服务费比例
    status             SMALLINT  NOT NULL DEFAULT 1,
    check_status       SMALLINT  NOT NULL DEFAULT 1,
    deleted            SMALLINT  NOT NULL DEFAULT 0,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          UUID,
    update_by          UUID
);
CREATE INDEX IF NOT EXISTS idx_product_product_id ON product(product_id);
CREATE INDEX IF NOT EXISTS idx_product_status     ON product(status);
CREATE INDEX IF NOT EXISTS idx_product_check_st   ON product(check_status);
CREATE INDEX IF NOT EXISTS idx_product_first_cid  ON product(first_cid);
CREATE INDEX IF NOT EXISTS idx_product_deleted    ON product(deleted);

CREATE TABLE IF NOT EXISTS colonel_activity_product (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id           VARCHAR(50) NOT NULL,
    activity_name         VARCHAR(500),
    product_id            VARCHAR(50) NOT NULL,
    title                 VARCHAR(500),
    price                 BIGINT,
    cos_ratio             NUMERIC(5,2),
    cos_fee               BIGINT,
    service_ratio         NUMERIC(5,2),
    status                SMALLINT NOT NULL DEFAULT 1,
    shop_id               BIGINT,
    shop_name             VARCHAR(200),
    activity_start_time   TIMESTAMP,
    activity_end_time     TIMESTAMP,
    promotion_start_time  TIMESTAMP,
    promotion_end_time    TIMESTAMP,
    months_of_protection  INT,
    cover                 VARCHAR(1000),
    detail_url            VARCHAR(1000),
    first_cid             BIGINT,
    second_cid            BIGINT,
    third_cid             BIGINT,
    deleted               SMALLINT  NOT NULL DEFAULT 0,
    create_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by             UUID,
    update_by             UUID,
    extra_data            JSONB,
    assignee_id           UUID,                              -- 招商负责人
    sample_requirement    JSONB,                             -- 寄样要求配置
    promotion_info        JSONB,                             -- 推广补充信息
    audit_status          SMALLINT NOT NULL DEFAULT 0,       -- 0=待分配,1=待审核,2=通过,3=拒绝,4=撤回
    audit_time            TIMESTAMP,                          -- 审核时间
    audit_remark          TEXT,                               -- 审核备注
    min_refer_amount      BIGINT                              -- [V1.3] 最低推广金额（分）
);
CREATE INDEX IF NOT EXISTS idx_ap_activity_id   ON colonel_activity_product(activity_id);
CREATE INDEX IF NOT EXISTS idx_ap_product_id    ON colonel_activity_product(product_id);
CREATE INDEX IF NOT EXISTS idx_ap_shop_id       ON colonel_activity_product(shop_id);
CREATE INDEX IF NOT EXISTS idx_ap_status        ON colonel_activity_product(status);
CREATE INDEX IF NOT EXISTS idx_ap_deleted       ON colonel_activity_product(deleted);
CREATE INDEX IF NOT EXISTS idx_ap_assignee_id   ON colonel_activity_product(assignee_id);
CREATE INDEX IF NOT EXISTS idx_ap_audit_status  ON colonel_activity_product(audit_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_activity_product ON colonel_activity_product(activity_id, product_id);

-- =============================================
-- 4. 达人管理
-- =============================================

CREATE TABLE IF NOT EXISTS talent (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    douyin_uid        VARCHAR(50)  NOT NULL UNIQUE,
    nickname          VARCHAR(200),
    fans_count        BIGINT       DEFAULT 0,
    fans_level        VARCHAR(20),
    live_level        VARCHAR(20),                        -- 带货等级
    avatar_url        VARCHAR(1000),
    intro             TEXT,
    categories        JSONB,
    contact_phone     VARCHAR(50),
    contact_wechat    VARCHAR(100),
    addr_city         VARCHAR(100),
    addr_district     VARCHAR(100),
    crawl_source      VARCHAR(20),
    crawl_status      SMALLINT  NOT NULL DEFAULT 0,      -- [V1.2] 0=待采集,1=成功,2=失败
    crawl_message     VARCHAR(500),                      -- [V1.2] 采集失败原因
    last_crawl_at     TIMESTAMP,
    status            SMALLINT  NOT NULL DEFAULT 1,
    deleted           SMALLINT  NOT NULL DEFAULT 0,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         UUID,
    update_by         UUID,
    extra_data        JSONB,
    likes_count       BIGINT     DEFAULT 0,               -- 获赞数
    following_count   BIGINT     DEFAULT 0,               -- 关注数
    works_count       BIGINT     DEFAULT 0,               -- 作品数
    ip_location       VARCHAR(100)                        -- IP属地
);
CREATE INDEX IF NOT EXISTS idx_talent_douyin_uid ON talent(douyin_uid);
CREATE INDEX IF NOT EXISTS idx_talent_fans_count ON talent(fans_count);
CREATE INDEX IF NOT EXISTS idx_talent_crawl_status ON talent(crawl_status);
CREATE INDEX IF NOT EXISTS idx_talent_status     ON talent(status);
CREATE INDEX IF NOT EXISTS idx_talent_deleted    ON talent(deleted);
CREATE INDEX IF NOT EXISTS idx_talent_crawl_src  ON talent(crawl_source);

CREATE TABLE IF NOT EXISTS talent_claim (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id        UUID NOT NULL,
    talent_uid       VARCHAR(50) NOT NULL,
    user_id          UUID NOT NULL,
    dept_id          UUID,
    claim_type       SMALLINT NOT NULL,
    status           SMALLINT NOT NULL DEFAULT 1,
    apply_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time     TIMESTAMP,
    expire_time      TIMESTAMP,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    remark           TEXT,
    CONSTRAINT fk_tc_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_tc_talent_id  ON talent_claim(talent_id);
CREATE INDEX IF NOT EXISTS idx_tc_user_id    ON talent_claim(user_id);
CREATE INDEX IF NOT EXISTS idx_tc_dept_id    ON talent_claim(dept_id);
CREATE INDEX IF NOT EXISTS idx_tc_status     ON talent_claim(status);
CREATE INDEX IF NOT EXISTS idx_tc_expire     ON talent_claim(expire_time) WHERE status = 1;
CREATE INDEX IF NOT EXISTS idx_tc_deleted    ON talent_claim(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tc_active ON talent_claim(talent_id, user_id) WHERE status IN (1, 2);

CREATE TABLE IF NOT EXISTS exclusive_talent (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    talent_id          UUID NOT NULL,
    talent_uid         VARCHAR(50) NOT NULL,
    user_id            UUID NOT NULL,
    dept_id            UUID NOT NULL,
    exclusive_type     SMALLINT NOT NULL,
    effective_month    VARCHAR(7) NOT NULL,
    service_fee        BIGINT   NOT NULL,
    channel_total_fee  BIGINT   NOT NULL,
    service_fee_ratio  NUMERIC(5,2) NOT NULL,
    monthly_samples    INT      NOT NULL,
    start_date         DATE     NOT NULL,
    end_date           DATE,
    status             SMALLINT NOT NULL DEFAULT 1,
    deleted            SMALLINT  NOT NULL DEFAULT 0,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          UUID,
    update_by          UUID,
    remark             TEXT,
    trigger_type       SMALLINT NOT NULL DEFAULT 1,       -- 1=自动触发, 2=人工审核
    audit_user_id      UUID,                               -- 审核人
    CONSTRAINT fk_et_talent FOREIGN KEY (talent_id) REFERENCES talent(id) ON DELETE CASCADE,
    CONSTRAINT fk_et_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX IF NOT EXISTS idx_et_talent_id      ON exclusive_talent(talent_id);
CREATE INDEX IF NOT EXISTS idx_et_user_id        ON exclusive_talent(user_id);
CREATE INDEX IF NOT EXISTS idx_et_dept_id        ON exclusive_talent(dept_id);
CREATE INDEX IF NOT EXISTS idx_et_effective_mon   ON exclusive_talent(effective_month);
CREATE INDEX IF NOT EXISTS idx_et_status         ON exclusive_talent(status);
CREATE INDEX IF NOT EXISTS idx_et_deleted        ON exclusive_talent(deleted);
CREATE INDEX IF NOT EXISTS idx_et_trigger_type   ON exclusive_talent(trigger_type);
CREATE INDEX IF NOT EXISTS idx_et_audit_user     ON exclusive_talent(audit_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_et_talent_month ON exclusive_talent(talent_id, effective_month);

CREATE TABLE IF NOT EXISTS exclusive_merchant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         VARCHAR(50) NOT NULL,
    merchant_name       VARCHAR(200),
    shop_id             BIGINT,
    user_id             UUID NOT NULL,
    dept_id             UUID NOT NULL,
    effective_month     VARCHAR(7) NOT NULL,
    service_fee         BIGINT   NOT NULL,
    business_total_fee  BIGINT   NOT NULL,
    service_fee_ratio   NUMERIC(5,2) NOT NULL,
    start_date          DATE     NOT NULL,
    end_date            DATE,
    status              SMALLINT NOT NULL DEFAULT 1,
    deleted             SMALLINT  NOT NULL DEFAULT 0,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    remark              TEXT
);
CREATE INDEX IF NOT EXISTS idx_em_merchant_id    ON exclusive_merchant(merchant_id);
CREATE INDEX IF NOT EXISTS idx_em_shop_id        ON exclusive_merchant(shop_id);
CREATE INDEX IF NOT EXISTS idx_em_user_id       ON exclusive_merchant(user_id);
CREATE INDEX IF NOT EXISTS idx_em_dept_id       ON exclusive_merchant(dept_id);
CREATE INDEX IF NOT EXISTS idx_em_effective_mon  ON exclusive_merchant(effective_month);
CREATE INDEX IF NOT EXISTS idx_em_status         ON exclusive_merchant(status);
CREATE INDEX IF NOT EXISTS idx_em_deleted       ON exclusive_merchant(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_em_merchant_month ON exclusive_merchant(merchant_id, effective_month);

-- =============================================
-- 5. 商家管理
-- =============================================

CREATE TABLE IF NOT EXISTS merchant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     VARCHAR(50)  NOT NULL UNIQUE,         -- 抖店商家ID
    merchant_name   VARCHAR(200),
    shop_id         BIGINT,                                 -- 关联店铺ID
    shop_name       VARCHAR(200),
    source_order_id VARCHAR(50),                           -- 首次来源订单ID
    status          SMALLINT  NOT NULL DEFAULT 1,          -- [V1.3] 1=启用, 0=禁用
    deleted         SMALLINT  NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    extra_data      JSONB
);
CREATE INDEX IF NOT EXISTS idx_merchant_merchant_id ON merchant(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_shop_id     ON merchant(shop_id);
CREATE INDEX IF NOT EXISTS idx_merchant_deleted     ON merchant(deleted);

-- =============================================
-- 6. 归因与推广
-- =============================================

CREATE TABLE IF NOT EXISTS pick_source_mapping (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL,                       -- 渠道用户ID
    short_id         VARCHAR(10) NOT NULL UNIQUE,         -- [V1.3] 方案B透传ID（8位Base36，≤10字符）
    uuid_seed        UUID,                                -- [V1.3] 方案B原始UUID，反查还原
    dept_id          UUID,
    pick_source      VARCHAR(128) NOT NULL,               -- [V1.2] 扩容至128（API实际返回可能较长）
    product_id       VARCHAR(50),
    activity_id      VARCHAR(50),
    source_url       TEXT,
    converted_url    TEXT,
    click_count      INT       DEFAULT 0,
    order_count      INT       DEFAULT 0,
    order_amount     BIGINT    DEFAULT 0,
    pick_extra       VARCHAR(10),                         -- [V1.3] 实际透传值=short_id（≤10字符）
    valid_from       TIMESTAMP NOT NULL,
    valid_until      TIMESTAMP NOT NULL,
    status           SMALLINT NOT NULL DEFAULT 1,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_psm_pick_source ON pick_source_mapping(pick_source);  -- [V1.2] 改为唯一索引
CREATE INDEX IF NOT EXISTS idx_psm_user_id      ON pick_source_mapping(user_id);
CREATE INDEX IF NOT EXISTS idx_psm_short_id     ON pick_source_mapping(short_id);        -- [V1.3] 方案B透传查询
CREATE INDEX IF NOT EXISTS idx_psm_uuid_seed    ON pick_source_mapping(uuid_seed);       -- [V1.3] 方案B UUID反查
CREATE INDEX IF NOT EXISTS idx_psm_dept_id      ON pick_source_mapping(dept_id);
CREATE INDEX IF NOT EXISTS idx_psm_product_id   ON pick_source_mapping(product_id);
CREATE INDEX IF NOT EXISTS idx_psm_valid_until  ON pick_source_mapping(valid_until);
CREATE INDEX IF NOT EXISTS idx_psm_status       ON pick_source_mapping(status);
CREATE INDEX IF NOT EXISTS idx_psm_deleted      ON pick_source_mapping(deleted);

-- =============================================
-- 7. 寄样管理
-- =============================================

CREATE TABLE IF NOT EXISTS sample_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_no           VARCHAR(50)  NOT NULL UNIQUE,
    talent_id            UUID NOT NULL,
    talent_uid           VARCHAR(50),
    talent_nickname      VARCHAR(200),
    product_id           UUID NOT NULL,
    activity_product_id  VARCHAR(50),
    activity_id          VARCHAR(50),
    user_id              UUID NOT NULL,
    dept_id              UUID,
    channel_user_id      UUID,
    channel_dept_id      UUID,
    recipient_name       VARCHAR(100),                    -- [V1.2] 收件人姓名
    recipient_phone      VARCHAR(32),                     -- [V1.2] 收件人电话
    recipient_address    VARCHAR(512),                    -- [V1.2] 收件人地址
    expected_sample_num  INT      DEFAULT 1,
    actual_sample_num    INT      DEFAULT 0,
    logistics_company    VARCHAR(50),
    tracking_no          VARCHAR(100),
    status               SMALLINT NOT NULL DEFAULT 1,
    audit_remark         TEXT,
    reject_reason        VARCHAR(500),
    sample_fee           BIGINT   DEFAULT 0,
    audit_time           TIMESTAMP,
    ship_time            TIMESTAMP,
    deliver_time         TIMESTAMP,
    homework_deadline    TIMESTAMP,                         -- 作业截止时间
    complete_time        TIMESTAMP,
    close_time           TIMESTAMP,
    close_reason         VARCHAR(500),
    deleted              SMALLINT  NOT NULL DEFAULT 0,
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by            UUID,
    update_by            UUID,
    remark               TEXT,
    extra_data           JSONB,
    CONSTRAINT fk_sr_talent FOREIGN KEY (talent_id) REFERENCES talent(id),
    CONSTRAINT fk_sr_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_sr_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX IF NOT EXISTS idx_sr_request_no    ON sample_request(request_no);
CREATE INDEX IF NOT EXISTS idx_sr_talent_id     ON sample_request(talent_id);
CREATE INDEX IF NOT EXISTS idx_sr_product_id    ON sample_request(product_id);
CREATE INDEX IF NOT EXISTS idx_sr_user_id       ON sample_request(user_id);
CREATE INDEX IF NOT EXISTS idx_sr_dept_id       ON sample_request(dept_id);
CREATE INDEX IF NOT EXISTS idx_sr_channel_user  ON sample_request(channel_user_id);
CREATE INDEX IF NOT EXISTS idx_sr_status        ON sample_request(status);
CREATE INDEX IF NOT EXISTS idx_sr_tracking_no   ON sample_request(tracking_no) WHERE tracking_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sr_create_time   ON sample_request(create_time);
CREATE INDEX IF NOT EXISTS idx_sr_deleted       ON sample_request(deleted);
CREATE INDEX IF NOT EXISTS idx_sr_channel_talent_product_7d
    ON sample_request(channel_user_id, talent_id, product_id, create_time DESC)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sample_status_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id      UUID NOT NULL,
    from_status     SMALLINT,
    to_status       SMALLINT NOT NULL,
    operator_id     UUID,
    operate_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark          TEXT,
    CONSTRAINT fk_ssl_request FOREIGN KEY (request_id) REFERENCES sample_request(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ssl_request_id ON sample_status_log(request_id);
CREATE INDEX IF NOT EXISTS idx_ssl_operate_t  ON sample_status_log(operate_time);

-- =============================================
-- 8. 订单（按月分区）
-- =============================================

CREATE TABLE IF NOT EXISTS colonelsettlement_order (
    id                       UUID,
    order_id                 VARCHAR(50)  NOT NULL,
    product_id               VARCHAR(50),
    product_name             VARCHAR(500),
    shop_id                  BIGINT,
    shop_name                VARCHAR(200),
    order_amount             BIGINT  DEFAULT 0,                -- [V1.3] 订单金额（分）
    actual_amount            BIGINT  DEFAULT 0,                -- [V1.3] 实际结算金额（分）
    colonel_buyin_id         BIGINT,
    colonel_activity_id      VARCHAR(50),
    settle_colonel_commission BIGINT  DEFAULT 0,
    settle_colonel_tech_service_fee BIGINT DEFAULT 0,
    second_colonel_buyin_id  BIGINT,
    second_colonel_activity_id VARCHAR(50),
    settle_second_colonel_commission BIGINT DEFAULT 0,
    phase_id                 VARCHAR(50),
    order_status             SMALLINT,
    order_type               SMALLINT,
    settle_time              TIMESTAMP,
    cursor                   VARCHAR(100),
    pick_source              VARCHAR(20),
    channel_user_id          UUID,
    channel_dept_id          UUID,
    user_id                  UUID,
    dept_id                  UUID,
    create_time              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT  NOT NULL DEFAULT 0,
    extra_data               JSONB,
    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 创建初始分区（2026年4月 ~ 2027年3月，共12个月）
CREATE TABLE IF NOT EXISTS cso_2026_04 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS cso_2026_05 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS cso_2026_06 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS cso_2026_07 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS cso_2026_08 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS cso_2026_09 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS cso_2026_10 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS cso_2026_11 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS cso_2026_12 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS cso_2027_01 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE IF NOT EXISTS cso_2027_02 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE IF NOT EXISTS cso_2027_03 PARTITION OF colonelsettlement_order FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');

-- 各分区索引
DO $$
DECLARE
    part_name TEXT;
BEGIN
    FOREACH part_name IN ARRAY ARRAY[
        'cso_2026_04','cso_2026_05','cso_2026_06','cso_2026_07',
        'cso_2026_08','cso_2026_09','cso_2026_10','cso_2026_11',
        'cso_2026_12','cso_2027_01','cso_2027_02','cso_2027_03'
    ]
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_order_id ON %I (order_id)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_product_id ON %I (product_id)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_colonel_id ON %I (colonel_buyin_id)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_settle_time ON %I (settle_time)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_pick_source ON %I (pick_source)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_user_id ON %I (user_id)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_dept_id ON %I (dept_id)', part_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_create_time ON %I (create_time)', part_name);
    END LOOP;
END $$;

-- =============================================
-- 9. 分佣与提成
-- =============================================

CREATE TABLE IF NOT EXISTS commission_settlement (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settle_month     VARCHAR(7)  NOT NULL,
    user_id          UUID NOT NULL,
    dept_id          UUID,
    order_count      INT      NOT NULL DEFAULT 0,
    total_order_amount BIGINT DEFAULT 0,
    commission_amount BIGINT NOT NULL DEFAULT 0,
    tech_service_fee BIGINT DEFAULT 0,
    net_commission   BIGINT DEFAULT 0,
    status           SMALLINT NOT NULL DEFAULT 1,
    confirm_time     TIMESTAMP,
    settle_time      TIMESTAMP,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    remark           TEXT,
    extra_data       JSONB,
    CONSTRAINT fk_comm_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX IF NOT EXISTS idx_comm_settle_month ON commission_settlement(settle_month);
CREATE INDEX IF NOT EXISTS idx_comm_user_id     ON commission_settlement(user_id);
CREATE INDEX IF NOT EXISTS idx_comm_dept_id     ON commission_settlement(dept_id);
CREATE INDEX IF NOT EXISTS idx_comm_status      ON commission_settlement(status);
CREATE INDEX IF NOT EXISTS idx_comm_deleted     ON commission_settlement(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_comm_user_month ON commission_settlement(user_id, settle_month);

CREATE TABLE IF NOT EXISTS commission_config (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID,                                 -- 为空=全局配置
    commission_type  SMALLINT NOT NULL,                   -- 1=招商提成, 2=渠道提成
    ratio            NUMERIC(5,4) NOT NULL,               -- 提成比例（0.1500=15%）
    scope            VARCHAR(20) NOT NULL DEFAULT 'global', -- global/activity/product
    scope_id         VARCHAR(50),                          -- scope非global时必填
    valid_from       TIMESTAMP,
    valid_until      TIMESTAMP,
    status           SMALLINT NOT NULL DEFAULT 1,
    deleted          SMALLINT  NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    remark           TEXT
);
CREATE INDEX IF NOT EXISTS idx_cc_user_id    ON commission_config(user_id);
CREATE INDEX IF NOT EXISTS idx_cc_type      ON commission_config(commission_type);
CREATE INDEX IF NOT EXISTS idx_cc_scope     ON commission_config(scope);
CREATE INDEX IF NOT EXISTS idx_cc_scope_id  ON commission_config(scope_id);
CREATE INDEX IF NOT EXISTS idx_cc_validity  ON commission_config(valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_cc_status    ON commission_config(status);
CREATE INDEX IF NOT EXISTS idx_cc_deleted   ON commission_config(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cc_user_type_scope
    ON commission_config(user_id, commission_type, scope, scope_id)
    WHERE deleted = 0;

-- =============================================
-- 10. 系统配置
-- =============================================

CREATE TABLE IF NOT EXISTS system_config (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key    VARCHAR(100) NOT NULL UNIQUE,
    config_value  TEXT,
    config_type   VARCHAR(20),
    config_group  VARCHAR(50),
    config_name   VARCHAR(200),
    sort_order    INT       DEFAULT 0,
    status        SMALLINT  NOT NULL DEFAULT 1,
    deleted       SMALLINT  NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    remark        VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_sc_config_key    ON system_config(config_key);
CREATE INDEX IF NOT EXISTS idx_sc_config_group  ON system_config(config_group);
CREATE INDEX IF NOT EXISTS idx_sc_status        ON system_config(status);
CREATE INDEX IF NOT EXISTS idx_sc_deleted       ON system_config(deleted);

-- 种子数据：系统配置
INSERT INTO system_config (config_key, config_value, config_type, config_group, config_name, status)
VALUES
    ('talent.exclusive.service_fee_ratio',  '70',       'numeric', 'talent',     '独家达人服务费占比阈值',    1),
    ('talent.exclusive.monthly_samples',    '10',       'int',     'talent',     '独家达人月均寄样数阈值',    1),
    ('talent.protection_days',             '30',       'int',     'talent',     '达人保护期天数',           1),
    ('merchant.exclusive.service_fee_ratio','70',      'numeric', 'merchant',   '独家商家服务费占比阈值',    1),
    ('pick_source.validity_months',         '3',        'int',     'douyin',     '归因有效期月数',           1),
    ('douyin.token.refresh_ahead_minutes', '60',       'int',     'douyin',     'Token提前刷新分钟数',      1),
    ('order.sync.batch_size',              '500',      'int',     'douyin',     '订单同步批次大小',          1),
    ('sample.claim.expire_hours',         '72',       'int',     'sample',     '认领有效期小时数',          1),
    ('sample.restrict_days',              '7',        'int',     'sample',     '寄样限制天数',             1),
    ('sample.restrict_enabled',           'true',     'boolean', 'sample',     '寄样限制开关',             1),
    ('sample.default_standard',
     '{"min_30day_sales":30000,"min_level":"LV1"}', 'json', 'sample', '寄样默认标准', 1),
    ('commission.business_default_ratio', '0.15',     'numeric', 'commission', '招商默认提成比例',          1),
    ('commission.channel_default_ratio',  '0.15',     'numeric', 'commission', '渠道默认提成比例',          1)
ON CONFLICT (config_key) DO NOTHING;

-- 种子数据：默认角色
INSERT INTO sys_role (role_code, role_name, data_scope, status) VALUES
    ('admin',       '超级管理员',     3, 1),
    ('biz_leader',  '招商组长',       2, 1),
    ('biz_staff',   '招商专员',       1, 1),
    ('channel_leader','渠道组长',     2, 1),
    ('channel_staff', '渠道专员',     1, 1)
ON CONFLICT (role_code) DO NOTHING;

-- 种子数据：默认管理员（仅开发环境）
INSERT INTO sys_user (username, password, real_name, channel_code, status)
VALUES ('admin', crypt('admin123', gen_salt('bf', 12)), '系统管理员', 'admin', 1)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
         JOIN sys_role r ON r.role_code = 'admin'
WHERE u.username = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- =============================================
-- 11. 订单详情（解密信息）
-- =============================================

CREATE TABLE IF NOT EXISTS order_detail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(50)  NOT NULL,
    address         TEXT,
    province        VARCHAR(50),
    city            VARCHAR(50),
    district        VARCHAR(50),
    recipient_name  VARCHAR(100),
    phone_cipher    TEXT,
    phone_plain     VARCHAR(100),
    is_virtual_tel  SMALLINT NOT NULL DEFAULT 0,
    phone_no_a      VARCHAR(20),
    phone_no_b      VARCHAR(20),
    expire_time     BIGINT,
    id_card_cipher  TEXT,
    id_card_plain   VARCHAR(50),
    decrypt_status  SMALLINT NOT NULL DEFAULT 0,
    decrypt_msg     VARCHAR(500),
    decrypt_time    TIMESTAMP,
    deleted         SMALLINT  NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    extra_data      JSONB
);
-- [微调] order_id 改为唯一索引：一个订单只有一条解密信息
CREATE UNIQUE INDEX IF NOT EXISTS uk_od_order_id     ON order_detail(order_id);
CREATE INDEX IF NOT EXISTS idx_od_decrypt_st   ON order_detail(decrypt_status);
CREATE INDEX IF NOT EXISTS idx_od_phone_plain  ON order_detail(phone_plain);
CREATE INDEX IF NOT EXISTS idx_od_create_time  ON order_detail(create_time);
CREATE INDEX IF NOT EXISTS idx_od_deleted      ON order_detail(deleted);

-- =============================================
-- 12. 订单解密记录
-- =============================================

CREATE TABLE IF NOT EXISTS order_decrypt_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(50)  NOT NULL,
    data_type       SMALLINT NOT NULL,                   -- 1=地址, 2=姓名, 3=手机号, 4=身份证
    cipher_text     TEXT,
    decrypt_text    TEXT,
    is_virtual_tel  SMALLINT,
    phone_no_a      VARCHAR(20),
    phone_no_b      VARCHAR(20),
    expire_time     TIMESTAMP,
    decrypt_status  SMALLINT NOT NULL DEFAULT 0,          -- 0=未解密, 1=成功, 2=失败
    decrypt_msg     VARCHAR(500),
    deleted         SMALLINT  NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       UUID,
    update_by       UUID,
    extra_data      JSONB
);
CREATE INDEX IF NOT EXISTS idx_odr_order_id     ON order_decrypt_record(order_id);
CREATE INDEX IF NOT EXISTS idx_odr_data_type   ON order_decrypt_record(data_type);
CREATE INDEX IF NOT EXISTS idx_odr_decrypt_st  ON order_decrypt_record(decrypt_status);
CREATE INDEX IF NOT EXISTS idx_odr_create_time ON order_decrypt_record(create_time);
CREATE INDEX IF NOT EXISTS idx_odr_deleted     ON order_decrypt_record(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_odr_order_type
    ON order_decrypt_record(order_id, data_type)
    WHERE deleted = 0;

-- =============================================
-- 13. 操作日志（按月分区）
-- =============================================

CREATE TABLE IF NOT EXISTS operation_log (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID,
    username        VARCHAR(50),
    module          VARCHAR(50),
    action          VARCHAR(50),
    target_type     VARCHAR(50),
    target_id       VARCHAR(50),
    target_name     VARCHAR(200),
    content         TEXT,
    request_method  VARCHAR(10),
    request_url     VARCHAR(500),
    request_params  JSONB,
    request_body    JSONB,
    response_code   VARCHAR(20),
    response_body   JSONB,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    duration_ms     BIGINT,
    error_message   TEXT,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 日志分区：2026年4月 ~ 2027年3月（与订单分区对齐，共12个月）
CREATE TABLE IF NOT EXISTS op_log_2026_04 PARTITION OF operation_log FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS op_log_2026_05 PARTITION OF operation_log FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS op_log_2026_06 PARTITION OF operation_log FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS op_log_2026_07 PARTITION OF operation_log FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS op_log_2026_08 PARTITION OF operation_log FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS op_log_2026_09 PARTITION OF operation_log FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS op_log_2026_10 PARTITION OF operation_log FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS op_log_2026_11 PARTITION OF operation_log FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS op_log_2026_12 PARTITION OF operation_log FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS op_log_2027_01 PARTITION OF operation_log FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE IF NOT EXISTS op_log_2027_02 PARTITION OF operation_log FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE IF NOT EXISTS op_log_2027_03 PARTITION OF operation_log FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');

-- =============================================
-- 14. 分区自动管理函数
-- =============================================

-- 为订单表和日志表自动创建下月分区（建议由 pg_cron 每月1号调用）
CREATE OR REPLACE FUNCTION create_next_month_partitions()
RETURNS void AS $$
DECLARE
    next_month_start DATE;
    next_month_end   DATE;
    month_str        VARCHAR(7);
    cso_part_name    VARCHAR(20);
    ol_part_name     VARCHAR(20);
BEGIN
    next_month_start := date_trunc('month', CURRENT_DATE + INTERVAL '1 month');
    next_month_end   := date_trunc('month', CURRENT_DATE + INTERVAL '2 months');
    month_str        := to_char(next_month_start, 'YYYY_MM');
    cso_part_name    := 'cso_'  || month_str;
    ol_part_name     := 'op_log_' || month_str;

    -- 订单分区
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF colonelsettlement_order FOR VALUES FROM (%L) TO (%L)',
        cso_part_name, next_month_start, next_month_end
    );
    -- 订单分区索引
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_order_id ON %I (order_id)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_product_id ON %I (product_id)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_colonel_id ON %I (colonel_buyin_id)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_settle_time ON %I (settle_time)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_pick_source ON %I (pick_source)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_user_id ON %I (user_id)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_dept_id ON %I (dept_id)', cso_part_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_cso_create_time ON %I (create_time)', cso_part_name);

    -- 日志分区
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF operation_log FOR VALUES FROM (%L) TO (%L)',
        ol_part_name, next_month_start, next_month_end
    );

    RAISE NOTICE 'Created partitions: %, %', cso_part_name, ol_part_name;
END;
$$ LANGUAGE plpgsql;

-- =============================================
-- 15. 验证查询（建库后手动执行确认）
-- =============================================

-- 列出所有用户表
-- SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;

-- 验证分区表结构
-- SELECT parent.relname AS table_name, child.relname AS partition_name
--   FROM pg_inherits
--   JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
--   JOIN pg_class child  ON pg_inherits.inhrelid  = child.oid
--  ORDER BY parent.relname, child.relname;

-- 验证种子数据
-- SELECT config_key, config_value FROM system_config ORDER BY config_group, config_key;
-- SELECT role_code, role_name FROM sys_role ORDER BY data_scope DESC;
