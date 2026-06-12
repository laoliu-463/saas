-- 进一步诊断 PENDING 状态：检查 promotion_end_time 和 snapshot 数据
SELECT
  'PENDING 总数' AS metric, COUNT(*)::text AS value
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING'
UNION ALL
SELECT 'PENDING + selected_to_library=true', COUNT(*)::text
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING' AND selected_to_library=true
UNION ALL
SELECT 'PENDING + selected_to_library=false', COUNT(*)::text
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING' AND COALESCE(selected_to_library, FALSE) = FALSE
UNION ALL
SELECT 'PENDING + audit_status=2 (approved)', COUNT(*)::text
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING' AND audit_status=2
UNION ALL
SELECT 'PENDING + audit_status=1 (pending_audit)', COUNT(*)::text
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING' AND audit_status=1
UNION ALL
SELECT 'PENDING + manual_disabled=true', COUNT(*)::text
FROM product_operation_state WHERE deleted=0 AND display_status='PENDING' AND manual_disabled=true
UNION ALL
SELECT 'PENDING 关联 snapshot 数量', COUNT(ps.id)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING'
UNION ALL
SELECT 'PENDING 关联 snapshot status=1', COUNT(*)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING' AND ps.status = 1
UNION ALL
SELECT 'PENDING 关联 snapshot status=0', COUNT(*)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING' AND ps.status = 0
UNION ALL
SELECT 'PENDING 关联 snapshot 缺失', COUNT(*)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING' AND ps.id IS NULL
UNION ALL
SELECT 'PENDING + end_time 空字符串', COUNT(*)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING' AND (ps.promotion_end_time IS NULL OR ps.promotion_end_time = '')
UNION ALL
SELECT 'PENDING + end_time 非空', COUNT(*)::text
FROM product_operation_state pos
LEFT JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status='PENDING' AND ps.promotion_end_time IS NOT NULL AND ps.promotion_end_time <> ''
UNION ALL
SELECT 'PENDING 推广中但未展示 (status=1 + display!=DISPLAYING)', COUNT(*)::text
FROM product_operation_state pos
JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status <> 'DISPLAYING' AND ps.status = 1
UNION ALL
SELECT '推广中但未展示 (status=1 + PENDING)', COUNT(*)::text
FROM product_operation_state pos
JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status = 'PENDING' AND ps.status = 1
UNION ALL
SELECT '推广中但未展示 (status=1 + HIDDEN)', COUNT(*)::text
FROM product_operation_state pos
JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status = 'HIDDEN' AND ps.status = 1;
