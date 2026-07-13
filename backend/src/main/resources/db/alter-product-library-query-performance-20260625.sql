-- Product library infinite-scroll query indexes.
-- Idempotent DDL only; does not mutate product library business facts.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_pos_library_realtime_page
    ON product_operation_state (
        activity_id,
        assignee_id,
        pinned_until DESC,
        selected_at DESC,
        product_id
    )
    WHERE deleted = 0
      AND selected_to_library = TRUE
      AND display_status = 'DISPLAYING'
      AND COALESCE(manual_disabled, FALSE) = FALSE
      AND (audit_status IS NULL OR audit_status <> 3)
      AND (biz_status IS NULL OR UPPER(biz_status) <> 'REJECTED');

CREATE INDEX IF NOT EXISTS idx_ps_library_promoting_join_sort
    ON product_snapshot (activity_id, product_id, sync_time DESC)
    WHERE deleted = 0 AND status = 1;

CREATE INDEX IF NOT EXISTS idx_ps_activity_active_count
    ON product_snapshot (activity_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ps_library_product_exact
    ON product_snapshot (product_id)
    WHERE deleted = 0 AND status = 1;

CREATE INDEX IF NOT EXISTS idx_ps_library_title_trgm
    ON product_snapshot USING GIN (title gin_trgm_ops)
    WHERE deleted = 0 AND status = 1;

CREATE INDEX IF NOT EXISTS idx_ps_library_product_id_trgm
    ON product_snapshot USING GIN (product_id gin_trgm_ops)
    WHERE deleted = 0 AND status = 1;

CREATE INDEX IF NOT EXISTS idx_ps_library_shop_name_trgm
    ON product_snapshot USING GIN (shop_name gin_trgm_ops)
    WHERE deleted = 0 AND status = 1;

CREATE INDEX IF NOT EXISTS idx_ps_library_category_trgm
    ON product_snapshot USING GIN (category_name gin_trgm_ops)
    WHERE deleted = 0 AND status = 1;
