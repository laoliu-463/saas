-- 分类 PENDING 来源（real-pre 真实数据，promotion_end_time 是 varchar(64)）
-- A) 过期活动（活动已结束但商品状态未回退）：snapshot.status=1 (推广中) 但 promotion_end_time < now
-- B) 未选中（display_rule 未选中）：product_operation_state.display_status=PENDING, selected_to_library=false
-- C) 待重算（数据漂移需要重新同步）：所有其他 PENDING 情况
WITH pending AS (
  SELECT pos.id, pos.activity_id, pos.product_id, pos.selected_to_library, pos.audit_status, pos.biz_status,
         pos.display_status, pos.manual_disabled, pos.last_operation_at,
         ps.status AS snapshot_status,
         ps.promotion_end_time,
         ps.promotion_start_time,
         ps.sync_time,
         -- 解析 varchar(64) 形式的时间戳（兼容 yyyy-MM-dd HH:mm:ss / ISO 8601 / 等等）
         CASE
           WHEN ps.promotion_end_time ~ '^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}'
             THEN to_timestamp(replace(substring(ps.promotion_end_time from 1 for 19), 'T', ' '), 'YYYY-MM-DD HH24:MI:SS')
           WHEN ps.promotion_end_time ~ '^\d+$' AND length(ps.promotion_end_time) = 10
             THEN to_timestamp(ps.promotion_end_time::bigint)
           WHEN ps.promotion_end_time ~ '^\d{13}$'
             THEN to_timestamp(ps.promotion_end_time::bigint / 1000.0)
           ELSE NULL
         END AS end_ts
  FROM product_operation_state pos
  LEFT JOIN product_snapshot ps
    ON ps.activity_id = pos.activity_id
   AND ps.product_id = pos.product_id
   AND ps.deleted = 0
  WHERE pos.deleted = 0
    AND pos.display_status = 'PENDING'
)
SELECT
  COUNT(*) AS total_pending,
  -- A: 推广中(status=1) + 活动已结束
  COUNT(*) FILTER (WHERE snapshot_status = 1 AND end_ts IS NOT NULL AND end_ts < NOW()) AS A_expired_promoting,
  -- A2: 活动已结束但 upstream status != 1（可能上游已下架）
  COUNT(*) FILTER (WHERE (snapshot_status IS NULL OR snapshot_status <> 1) AND end_ts IS NOT NULL AND end_ts < NOW()) AS A2_expired_not_promoting,
  -- A3: 活动已结束且 end_ts 解析失败
  COUNT(*) FILTER (WHERE (snapshot_status IS NULL OR snapshot_status <> 1) AND end_ts IS NULL) AS A3_end_unparseable,
  -- B: 未选中 (selected_to_library=false)
  COUNT(*) FILTER (WHERE COALESCE(selected_to_library, FALSE) = FALSE) AS B_not_selected,
  -- C: 待重算 (其他：选中了+未过期)
  COUNT(*) FILTER (WHERE NOT (snapshot_status = 1 AND end_ts IS NOT NULL AND end_ts < NOW())
                    AND COALESCE(selected_to_library, FALSE) = TRUE) AS C_pending_resync,
  -- 推广中但未展示 (status=1 + display_status != DISPLAYING)
  (SELECT COUNT(*) FROM product_operation_state pos2 LEFT JOIN product_snapshot ps2 ON ps2.activity_id=pos2.activity_id AND ps2.product_id=pos2.product_id AND ps2.deleted=0 WHERE pos2.deleted=0 AND pos2.display_status <> 'DISPLAYING' AND ps2.status = 1) AS promoting_not_displaying
FROM pending;
