-- 商品库主链路改造（P0）
-- 执行前置：init-db.sql

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
    promote_link       TEXT,
    short_link         TEXT,
    promotion_scene    INTEGER,
    external_unique_id VARCHAR(128),
    last_operation_at  TIMESTAMP,
    deleted            SMALLINT  NOT NULL DEFAULT 0,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          UUID,
    update_by          UUID
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

