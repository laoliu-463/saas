-- 增量脚本：达人真实资料同步字段与同步日志
-- 适用：已存在 colonel_saas 数据库的环境

-- 1) talent 表补充真实资料同步字段（表名 talent，对外称 talents）
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

-- 2) 达人资料同步日志
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
