-- Backfill historical unattributed native colonel orders after colonel_order_info attribution fix.
-- Safe scope:
-- 1. only orders still marked UNATTRIBUTED / NO_PICK_SOURCE
-- 2. only rows whose extra_data already carries colonel_buyin_id
-- 3. only when current mapping facts prove there is no exact native/activity-product/native-any match

WITH stale AS (
    SELECT id,
           order_id,
           product_id,
           colonel_activity_id,
           extra_data ->> 'colonel_buyin_id' AS colonel_buyin_id
    FROM colonelsettlement_order
    WHERE deleted = 0
      AND attribution_status = 'UNATTRIBUTED'
      AND attribution_remark = 'NO_PICK_SOURCE'
      AND extra_data ? 'colonel_buyin_id'
),
classified AS (
    SELECT s.id,
           CASE
               WHEN EXISTS (
                   SELECT 1
                   FROM pick_source_mapping m
                   WHERE m.deleted = 0
                     AND m.status = 1
                     AND m.colonel_buyin_id = s.colonel_buyin_id
                     AND m.activity_id = s.colonel_activity_id
                     AND m.product_id = s.product_id
               ) THEN 'COLONEL_ORDER_INFO'
               WHEN EXISTS (
                   SELECT 1
                   FROM pick_source_mapping m
                   WHERE m.deleted = 0
                     AND m.status = 1
                     AND m.activity_id = s.colonel_activity_id
                     AND m.product_id = s.product_id
               ) THEN 'COLONEL_ORDER_INFO'
               WHEN EXISTS (
                   SELECT 1
                   FROM pick_source_mapping m
                   WHERE m.deleted = 0
                     AND m.status = 1
                     AND m.colonel_buyin_id = s.colonel_buyin_id
               ) THEN 'COLONEL_ORDER_INFO'
               ELSE 'COLONEL_MAPPING_NOT_FOUND'
           END AS target_reason
    FROM stale s
)
UPDATE colonelsettlement_order o
SET attribution_remark = c.target_reason,
    update_time = NOW()
FROM classified c
WHERE o.id = c.id
  AND c.target_reason = 'COLONEL_MAPPING_NOT_FOUND';
