-- 进一步分析 推广中但未展示（status=1, display!=DISPLAYING, 192个）
SELECT
  pos.display_status,
  pos.hidden_reason,
  pos.selected_to_library,
  pos.audit_status,
  pos.biz_status,
  pos.manual_disabled,
  COUNT(*) AS cnt
FROM product_operation_state pos
JOIN product_snapshot ps ON ps.activity_id=pos.activity_id AND ps.product_id=pos.product_id AND ps.deleted=0
WHERE pos.deleted=0 AND pos.display_status <> 'DISPLAYING' AND ps.status = 1
GROUP BY pos.display_status, pos.hidden_reason, pos.selected_to_library, pos.audit_status, pos.biz_status, pos.manual_disabled
ORDER BY cnt DESC;
