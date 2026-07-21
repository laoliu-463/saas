$ErrorActionPreference = "Stop"

$sql = @'
SELECT id,
       order_id,
       product_id,
       product_name,
       shop_id,
       shop_name,
       order_amount,
       actual_amount,
       settle_amount,
       estimate_service_fee,
       effective_service_fee,
       estimate_tech_service_fee,
       effective_tech_service_fee,
       estimate_service_fee_expense,
       effective_service_fee_expense,
       colonel_buyin_id,
       settle_colonel_commission,
       settle_colonel_tech_service_fee,
       second_colonel_buyin_id,
       second_colonel_activity_id,
       settle_second_colonel_commission,
       flow_point,
       phase_id,
       order_status,
       order_type,
       pick_source,
       cursor,
       talent_id,
       channel_user_id,
       channel_user_name,
       colonel_user_id,
       colonel_user_name,
       promotion_link_id,
       product_title,
       product_pic,
       talent_name,
       channel_dept_id,
       user_id,
       dept_id,
       colonel_activity_id,
       pay_time,
       order_create_time,
       settle_time,
       attribution_status,
       attribution_remark,
       channel_attribution_source,
       recruiter_attribution_source,
       channel_attribution_status,
       recruiter_attribution_status,
       create_time,
       update_time,
       version,
       deleted,
       extra_data
FROM colonelsettlement_order
WHERE deleted = 0
  AND colonel_activity_id IS NULL
  AND product_id IN (NULL, NULL, NULL, NULL, NULL)
LIMIT 0;
'@

$sql | docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
if ($LASTEXITCODE -ne 0) {
    throw "Activity query schema probe failed with exit code $LASTEXITCODE."
}

Write-Host "Activity query schema probe passed."
