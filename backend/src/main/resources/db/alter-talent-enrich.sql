-- 澧為噺鑴氭湰锛氳揪浜鸿嚜鍔ㄨˉ鍏ㄤ笌瀛楁鏉ユ簮瀹¤
-- 閫傜敤锛氬凡瀛樺湪 colonel_saas 鏁版嵁搴撶殑鐜锛屾墜宸ユ墽琛?
-- 1) talent 琛ュ厖瀛楁
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

-- 2) 自动补全任务表
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

-- 3) 字段来源审计表
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

-- 4) 达人授权表
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


