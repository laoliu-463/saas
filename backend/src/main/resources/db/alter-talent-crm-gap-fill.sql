-- 增量脚本：达人 CRM 表结构差异补齐
-- 执行前置：init-db.sql、alter-talent-enrich.sql
-- 说明：
-- 1) 仅做向后兼容新增，不破坏现有字段
-- 2) 主键/外键统一沿用现网 UUID 体系

-- 1) 达人联系方式（支持多条联系方式 + 可见范围）
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

-- 2) 达人标签
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

-- 3) 达人标签关系
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

-- 4) 达人采集日志
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

-- 5) talent 常用查询索引
CREATE INDEX IF NOT EXISTS idx_talent_douyin_no ON talent(douyin_no);
CREATE INDEX IF NOT EXISTS idx_talent_last_enrich_time ON talent(last_enrich_time);
