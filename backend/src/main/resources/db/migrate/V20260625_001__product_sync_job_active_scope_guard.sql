-- Guard manual activity product sync queue against duplicate active jobs.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT job_type, scope, COUNT(*) AS active_count
            FROM product_sync_job_log
            WHERE deleted = 0
              AND status IN ('QUEUED', 'RUNNING')
            GROUP BY job_type, scope
            HAVING COUNT(*) > 1
        ) duplicated_active_jobs
    ) THEN
        RAISE EXCEPTION 'Duplicate active product_sync_job_log rows exist; resolve them before adding ux_product_sync_job_log_active_scope';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_sync_job_log_active_scope
    ON product_sync_job_log(job_type, scope)
    WHERE deleted = 0
      AND status IN ('QUEUED', 'RUNNING');
