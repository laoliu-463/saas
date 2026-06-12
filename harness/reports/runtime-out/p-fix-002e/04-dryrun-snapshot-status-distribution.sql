-- PENDING 中 snapshot.status 分布
SELECT
  ps.status AS snapshot_status,
  ps.status_text,
  COUNT(*) AS cnt
FROM product_operation_state pos
JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING'
GROUP BY ps.status, ps.status_text
ORDER BY cnt DESC;
