-- COMMISSION-RULE-SOURCE-001 read-only probe (real-pre)
\set ON_ERROR_STOP on

\echo '=== system_config commission keys ==='
SELECT config_key, config_value, status, deleted, enabled, config_version
FROM system_config
WHERE deleted = 0 AND config_key LIKE 'commission%'
ORDER BY config_key;

\echo '=== commissions table count ==='
SELECT COUNT(*) AS commissions_count FROM commissions WHERE deleted = 0;

\echo '=== commission_config table count ==='
SELECT COUNT(*) AS commission_config_count FROM commission_config WHERE deleted = 0;

\echo '=== performance_records daily 2026-06-08..13 (create cohort) ==='
SELECT DATE(COALESCE(pr.order_create_time, co.create_time)) AS stat_date,
       COUNT(*) AS order_count,
       ROUND(SUM(pr.estimate_service_profit) / 100.0, 2) AS service_profit_yuan,
       ROUND(SUM(pr.estimate_recruiter_commission) / 100.0, 2) AS recruiter_yuan,
       ROUND(SUM(pr.estimate_channel_commission) / 100.0, 2) AS channel_yuan,
       ROUND(SUM(pr.estimate_gross_profit) / 100.0, 2) AS gross_profit_yuan,
       ROUND(SUM(pr.estimate_recruiter_commission + pr.estimate_channel_commission) / 100.0, 2) AS total_commission_yuan,
       ROUND(100.0 * SUM(pr.estimate_recruiter_commission + pr.estimate_channel_commission)
             / NULLIF(SUM(pr.estimate_service_profit), 0), 2) AS total_commission_rate_pct
FROM performance_records pr
LEFT JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
WHERE pr.is_valid = TRUE
  AND COALESCE(pr.order_create_time, co.create_time) >= TIMESTAMP '2026-06-08 00:00:00'
  AND COALESCE(pr.order_create_time, co.create_time) < TIMESTAMP '2026-06-14 00:00:00'
GROUP BY 1
ORDER BY 1;

\echo '=== commission rate snapshot distinct ==='
SELECT pr.recruiter_commission_rate, pr.channel_commission_rate, COUNT(*) AS cnt
FROM performance_records pr
LEFT JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
WHERE pr.is_valid = TRUE
  AND COALESCE(pr.order_create_time, co.create_time) >= TIMESTAMP '2026-06-08 00:00:00'
  AND COALESCE(pr.order_create_time, co.create_time) < TIMESTAMP '2026-06-14 00:00:00'
GROUP BY 1, 2
ORDER BY cnt DESC
LIMIT 10;

\echo '=== order summary current 15+15 calculation (create cohort) ==='
WITH buckets AS (
  SELECT DATE(create_time) AS stat_date,
         COALESCE(colonel_activity_id, '') AS activity_id,
         COALESCE(product_id, '') AS product_id,
         COALESCE(colonel_user_id, user_id)::text AS recruiter_user_id,
         GREATEST(SUM(COALESCE(estimate_service_fee, 0))
             - SUM(COALESCE(estimate_tech_service_fee, 0))
             - SUM(COALESCE(estimate_service_fee_expense, 0)), 0) AS bucket_net_cent
  FROM colonelsettlement_order
  WHERE deleted = 0
    AND create_time >= TIMESTAMP '2026-06-08 00:00:00'
    AND create_time < TIMESTAMP '2026-06-14 00:00:00'
  GROUP BY 1, 2, 3, 4
)
SELECT stat_date,
       COUNT(*) AS bucket_count,
       ROUND(SUM(bucket_net_cent) / 100.0, 2) AS service_profit_yuan,
       ROUND(SUM(ROUND(bucket_net_cent * 0.15) * 2) / 100.0, 2) AS total_commission_yuan,
       ROUND((SUM(bucket_net_cent) - SUM(ROUND(bucket_net_cent * 0.15) * 2)) / 100.0, 2) AS gross_profit_yuan,
       ROUND(100.0 * SUM(ROUND(bucket_net_cent * 0.15) * 2) / NULLIF(SUM(bucket_net_cent), 0), 2) AS total_rate_pct
FROM buckets
GROUP BY stat_date
ORDER BY stat_date;

\echo '=== dimension facts in order data ==='
SELECT DATE(create_time) AS stat_date,
       COUNT(DISTINCT colonel_activity_id) AS activity_count,
       COUNT(DISTINCT product_id) AS product_count,
       COUNT(DISTINCT COALESCE(colonel_user_id, user_id)) AS recruiter_count
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= TIMESTAMP '2026-06-08 00:00:00'
  AND create_time < TIMESTAMP '2026-06-14 00:00:00'
GROUP BY 1
ORDER BY 1;

\echo '=== dashboard_performance_daily schema ==='
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'dashboard_performance_daily'
ORDER BY ordinal_position;
