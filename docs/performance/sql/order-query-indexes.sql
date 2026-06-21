-- Order query performance indexes.
-- Safety:
-- 1) This file is a documentation SQL script, not an auto-applied migration.
-- 2) Do not run it directly on production without DBA review, EXPLAIN evidence and a maintenance plan.
-- 3) All indexes use IF NOT EXISTS and are non-destructive.
-- 4) If colonelsettlement_order is partitioned, create indexes on the parent table for PG11+
--    so child partitions inherit index definitions for future partitions.
-- 5) Do not use CREATE INDEX CONCURRENTLY inside a normal Flyway/Liquibase transaction.
--    For large real-pre/prod tables, execute CONCURRENTLY manually outside a transaction.

CREATE INDEX IF NOT EXISTS idx_cso_active_update_create
ON colonelsettlement_order (update_time DESC, create_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_active_order_id_create
ON colonelsettlement_order (order_id, create_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_active_user_update_create
ON colonelsettlement_order (user_id, update_time DESC, create_time DESC)
WHERE deleted = 0 AND user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_dept_update_create
ON colonelsettlement_order (dept_id, update_time DESC, create_time DESC)
WHERE deleted = 0 AND dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_channel_dept_update_create
ON colonelsettlement_order (channel_dept_id, update_time DESC, create_time DESC)
WHERE deleted = 0 AND channel_dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_attr_update_create
ON colonelsettlement_order (attribution_status, update_time DESC, create_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_active_order_status_update_create
ON colonelsettlement_order (order_status, update_time DESC, create_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_active_product_update_create
ON colonelsettlement_order (product_id, update_time DESC, create_time DESC)
WHERE deleted = 0 AND product_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_activity_update_create
ON colonelsettlement_order (colonel_activity_id, update_time DESC, create_time DESC)
WHERE deleted = 0 AND colonel_activity_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_create_update
ON colonelsettlement_order (create_time DESC, update_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_active_settle_update
ON colonelsettlement_order (settle_time DESC, update_time DESC)
WHERE deleted = 0 AND settle_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cso_active_unattr_reason
ON colonelsettlement_order (attribution_remark)
WHERE deleted = 0
  AND attribution_status = 'UNATTRIBUTED'
  AND attribution_remark IS NOT NULL;
