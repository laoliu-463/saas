-- 活动列表异步同步任务日志表
-- 设计目标：活动列表/状态同步独立于商品同步，拥有自己的异步 job 跟踪

CREATE TABLE IF NOT EXISTS colonel_activity_sync_job_log (
    id              UUID PRIMARY KEY,
    job_id          VARCHAR(64)  NOT NULL,
    sync_type       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVITY_LIST',
    scope           VARCHAR(64)  NOT NULL DEFAULT 'ACTIVITY_LIST_GLOBAL',
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    triggered_by    UUID,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    activities_total    INT DEFAULT 0,
    activities_synced   INT DEFAULT 0,
    activities_failed   INT DEFAULT 0,
    error_message       TEXT,
    metadata_json       TEXT,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INT NOT NULL DEFAULT 0
);

-- 兼容早期已创建但尚未包含 scope 的任务日志表。
ALTER TABLE colonel_activity_sync_job_log
    ADD COLUMN IF NOT EXISTS scope VARCHAR(64) NOT NULL DEFAULT 'ACTIVITY_LIST_GLOBAL';

ALTER TABLE colonel_activity_sync_job_log
    ALTER COLUMN scope SET DEFAULT 'ACTIVITY_LIST_GLOBAL';

UPDATE colonel_activity_sync_job_log
   SET scope = 'ACTIVITY_LIST_GLOBAL'
 WHERE scope IS NULL;

ALTER TABLE colonel_activity_sync_job_log
    ALTER COLUMN scope SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_colonel_activity_sync_job_log_job_id
    ON colonel_activity_sync_job_log(job_id);

CREATE INDEX IF NOT EXISTS idx_colonel_activity_sync_job_log_status
    ON colonel_activity_sync_job_log(status, create_time);

-- P8.4 修复: partial unique index 防止同 scope 出现多个活跃任务
-- (用 WHERE 子句限定只对 QUEUED/RUNNING 状态约束, 历史 SUCCESS/FAILED 不影响)
CREATE UNIQUE INDEX IF NOT EXISTS idx_colonel_activity_sync_job_log_active_scope
    ON colonel_activity_sync_job_log(sync_type, scope)
    WHERE status IN ('QUEUED', 'RUNNING') AND deleted = 0;

COMMENT ON TABLE colonel_activity_sync_job_log IS '活动列表异步同步任务日志';

-- colonel_activity 增加活动状态独立同步时间
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'colonel_activity'
          AND column_name = 'activity_status_synced_at'
    ) THEN
        ALTER TABLE colonel_activity
            ADD COLUMN activity_status_synced_at TIMESTAMP;
        COMMENT ON COLUMN colonel_activity.activity_status_synced_at
            IS '活动状态（列表同步/详情同步）最后同步时间，独立于商品同步的 last_sync_at';
    END IF;
END $$;
