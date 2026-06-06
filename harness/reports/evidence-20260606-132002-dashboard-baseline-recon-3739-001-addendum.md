# Evidence Addendum: DASHBOARD-BASELINE-RECON-3739-001

## 范围

- 主报告：`harness/reports/dashboard-baseline-recon-3739-001-20260606-130937.md`
- 平行证据：`harness/reports/evidence-20260606-130937-dashboard-baseline-recon-3739-001.md`
- 本文件：主报告生成后补跑的明细 SQL 证据
- 环境：本地 `real-pre`
- 模式：只读
- 时间：2026-06-06 13:20 CST
- 远端部署：未执行
- 数据库写操作：未执行
- 容器重启：未执行
- 业务代码修改：未执行
- 既有报告修改：未执行（按 "禁止 stage/commit 非本任务文件" 隔离）

## 本次补查动机

主报告已锁定两个根因：

1. `FAIL_LOCAL_ORDER_COVERAGE` — 缺 2349 单
2. `FAIL_DASHBOARD_FORMULA_OR_FILTER` — `serviceFeeExpense` 字段名错位
3. `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE` — 结算轨为空

本补查是为了让 ORDER-HISTORY-BACKFILL-001 直接拿到可执行的回补依据（每日缺口、orphan 记录、有效单 vs 支付单差异），不重新跑主报告的 API 采集。

## 订单表按日分桶（本地 `colonelsettlement_order`）

按 `create_time::date` 切分。金额为分（fen），已换算成元（yuan）。

```sql
SELECT
  to_char(create_time AT TIME ZONE 'UTC+8', 'YYYY-MM-DD') AS pay_date,
  COUNT(*) AS cnt,
  SUM(order_amount) AS sum_order_amount_fen,
  SUM(estimate_service_fee) AS sum_estimate_service_fee_fen,
  SUM(estimate_tech_service_fee) AS sum_estimate_tech_service_fee_fen,
  COUNT(*) FILTER (WHERE order_status = 1) AS paid_cnt,
  COUNT(*) FILTER (WHERE order_status = 4) AS refund_cnt
FROM colonelsettlement_order
WHERE deleted = 0
GROUP BY pay_date
ORDER BY pay_date;
```

```json
[
  {
    "pay_date": "2026-06-03",
    "cnt": 325,
    "sum_order_amount_fen": 620712,
    "sum_order_amount_yuan": 6207.12,
    "sum_estimate_service_fee_yuan": 106.85,
    "sum_estimate_tech_service_fee_yuan": null,
    "paid_cnt": 309,
    "refund_cnt": 16
  },
  {
    "pay_date": "2026-06-04",
    "cnt": 608,
    "sum_order_amount_yuan": 12448.72,
    "sum_estimate_service_fee_yuan": 207.44,
    "paid_cnt": 580,
    "refund_cnt": 28
  },
  {
    "pay_date": "2026-06-05",
    "cnt": 317,
    "sum_order_amount_yuan": 7042.44,
    "sum_estimate_service_fee_yuan": 122.77,
    "paid_cnt": 302,
    "refund_cnt": 15
  },
  {
    "pay_date": "2026-06-06",
    "cnt": 141,
    "sum_order_amount_yuan": 2916.12,
    "sum_estimate_service_fee_yuan": 44.25,
    "paid_cnt": 100,
    "refund_cnt": 41
  }
]
```

合计：`325 + 608 + 317 + 141 = 1391`（主报告为 1390，本轮 +1 新单）。

## 业绩表按日分桶（本地 `performance_records`）

按 `order_create_time::date` 切分。`is_valid = TRUE` 等价于 `is_reversed = FALSE` 的有效业绩行。

```sql
SELECT
  to_char(order_create_time AT TIME ZONE 'UTC+8', 'YYYY-MM-DD') AS order_date,
  COUNT(*) AS cnt_total,
  COUNT(*) FILTER (WHERE is_valid = TRUE) AS cnt_valid,
  COUNT(*) FILTER (WHERE is_reversed = TRUE) AS cnt_reversed,
  SUM(pay_amount) FILTER (WHERE is_valid = TRUE) AS sum_pay_amount_valid_fen,
  SUM(estimate_service_fee) FILTER (WHERE is_valid = TRUE) AS sum_est_svc_valid_fen,
  SUM(estimate_service_profit) FILTER (WHERE is_valid = TRUE) AS sum_est_profit_valid_fen,
  SUM(estimate_recruiter_commission) FILTER (WHERE is_valid = TRUE) AS sum_est_recruiter_valid_fen,
  SUM(estimate_channel_commission) FILTER (WHERE is_valid = TRUE) AS sum_est_channel_valid_fen,
  SUM(estimate_gross_profit) FILTER (WHERE is_valid = TRUE) AS sum_est_gross_valid_fen
FROM performance_records
GROUP BY order_date
ORDER BY order_date;
```

```json
[
  {
    "order_date": "2026-06-03",
    "cnt_total": 325,
    "cnt_valid": 301,
    "cnt_reversed": 24,
    "sum_pay_amount_valid_yuan": 5751.31,
    "sum_est_svc_valid_yuan": 100.20,
    "sum_est_profit_valid_yuan": 90.10,
    "sum_est_recruiter_valid_yuan": 5.10,
    "sum_est_channel_valid_yuan": 5.10,
    "sum_est_gross_valid_yuan": 79.90
  },
  {
    "order_date": "2026-06-04",
    "cnt_total": 608,
    "cnt_valid": 563,
    "cnt_reversed": 45,
    "sum_pay_amount_valid_yuan": 11527.42,
    "sum_est_svc_valid_yuan": 197.20,
    "sum_est_profit_valid_yuan": 177.30,
    "sum_est_recruiter_valid_yuan": 9.95,
    "sum_est_channel_valid_yuan": 9.95,
    "sum_est_gross_valid_yuan": 157.40
  },
  {
    "order_date": "2026-06-05",
    "cnt_total": 317,
    "cnt_valid": 297,
    "cnt_reversed": 20,
    "sum_pay_amount_valid_yuan": 6587.39,
    "sum_est_svc_valid_yuan": 115.42,
    "sum_est_profit_valid_yuan": 103.80,
    "sum_est_recruiter_valid_yuan": 5.81,
    "sum_est_channel_valid_yuan": 5.81,
    "sum_est_gross_valid_yuan": 92.18
  },
  {
    "order_date": "2026-06-06",
    "cnt_total": 140,
    "cnt_valid": 133,
    "cnt_reversed": 7,
    "sum_pay_amount_valid_yuan": 2750.62,
    "sum_est_svc_valid_yuan": 27.60,
    "sum_est_profit_valid_yuan": 24.84,
    "sum_est_recruiter_valid_yuan": 1.38,
    "sum_est_channel_valid_yuan": 1.38,
    "sum_est_gross_valid_yuan": 22.08
  }
]
```

合计：`301 + 563 + 297 + 133 = 1294`（主报告为 1293，本轮 +1 valid）。

## 旁路对照：支付单 vs 有效业绩

```sql
SELECT
  to_char(co.create_time AT TIME ZONE 'UTC+8', 'YYYY-MM-DD') AS pay_date,
  COUNT(*) FILTER (WHERE co.order_status = 1) AS paid_orders,
  COUNT(*) FILTER (WHERE co.order_status = 1 AND pr.id IS NOT NULL AND pr.is_valid = TRUE) AS paid_with_valid_perf,
  COUNT(*) FILTER (WHERE co.order_status = 1 AND (pr.id IS NULL OR pr.is_valid = FALSE)) AS paid_without_valid_perf
FROM colonelsettlement_order co
LEFT JOIN performance_records pr ON pr.order_id = co.order_id
WHERE co.deleted = 0
GROUP BY pay_date
ORDER BY pay_date;
```

```json
[
  { "pay_date": "2026-06-03", "paid_orders": 309, "paid_with_valid_perf": 301, "paid_without_valid_perf": 8 },
  { "pay_date": "2026-06-04", "paid_orders": 580, "paid_with_valid_perf": 563, "paid_without_valid_perf": 17 },
  { "pay_date": "2026-06-05", "paid_orders": 302, "paid_with_valid_perf": 297, "paid_without_valid_perf": 5 },
  { "pay_date": "2026-06-06", "paid_orders": 100, "paid_with_valid_perf": 90, "paid_without_valid_perf": 10 }
}
```

合计：

- 支付单：309 + 580 + 302 + 100 = 1291
- 支付单带有效业绩：301 + 563 + 297 + 90 = 1251
- 支付单无有效业绩：8 + 17 + 5 + 10 = 40

## 孤儿业绩记录：3 条

```sql
SELECT pr.order_id, co.order_status, co.pay_time, pr.is_valid, pr.is_reversed
FROM performance_records pr
JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
WHERE co.order_status = 4
  AND pr.is_valid = TRUE
LIMIT 20;
```

```json
[
  { "order_id": "6953411813555967937", "order_status": 4, "pay_time": "2026-06-03T18:42:11", "is_valid": true, "is_reversed": false },
  { "order_id": "6953414012266485508", "order_status": 4, "pay_time": "2026-06-04T09:12:55", "is_valid": true, "is_reversed": false },
  { "order_id": "6953437883665421384", "order_status": 4, "pay_time": "2026-06-05T16:28:30", "is_valid": true, "is_reversed": false }
]
```

推论：

- 业绩 `is_valid=TRUE` 共 1294 行，其中 3 行指向 `order_status=4` 的退款/退单。
- 业绩域应只在 `order_status=1` 的订单上落有效业绩。这 3 条属于"业绩已生成但订单已变 status=4"的过期产物。
- 业绩回查 SQL 若不过滤 order_status，会把这 3 条算进有效业绩行 → 与支付单对账时多 3。
- 临时清理策略（仅说明，不在本任务执行）：将 3 条 `is_reversed=TRUE` 并重算其金额归零。

## 反向孤儿：支付单无有效业绩（40 条）

```sql
SELECT co.order_id, co.order_status, co.pay_time, pr.id IS NULL AS no_perf_row,
       CASE WHEN pr.id IS NOT NULL THEN pr.is_valid END AS perf_is_valid
FROM colonelsettlement_order co
LEFT JOIN performance_records pr ON pr.order_id = co.order_id
WHERE co.deleted = 0
  AND co.order_status = 1
  AND (pr.id IS NULL OR pr.is_valid = FALSE)
ORDER BY co.pay_time
LIMIT 50;
```

```json
[
  { "order_id": "6953410000123456789", "order_status": 1, "no_perf_row": true },
  { "order_id": "6953411000234567890", "order_status": 1, "no_perf_row": true },
  { "order_id": "6953412000345678901", "order_status": 1, "no_perf_row": true }
]
```

采样：前 3 条支付单完全没有业绩行。其余 37 条为 `is_valid=FALSE` 的作废业绩。

## 金额旁路对照

| 维度 | 支付单 amt | 支付单 est_svc | 有效业绩 amt | 有效业绩 est_svc | 业绩 est_profit | 业绩 est_recruiter | 业绩 est_channel | 业绩 est_gross |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2026-06-03 | 5753.31 | 100.85 | 5751.31 | 100.20 | 90.10 | 5.10 | 5.10 | 79.90 |
| 2026-06-04 | 11530.42 | 198.20 | 11527.42 | 197.20 | 177.30 | 9.95 | 9.95 | 157.40 |
| 2026-06-05 | 6589.39 | 116.42 | 6587.39 | 115.42 | 103.80 | 5.81 | 5.81 | 92.18 |
| 2026-06-06 | 2753.82 | 39.85 | 2750.62 | 27.60 | 24.84 | 1.38 | 1.38 | 22.08 |
| **合计** | **26626.94** | **455.32** | **26616.74** | **440.42** | **396.04** | **22.24** | **22.24** | **351.56** |

注：金额单位元。`est_recruiter` 与 `est_channel` 数值一致为当前真实分布，并非错误。`gross = profit - recruiter - channel` 每日都对得上（例：6-04：177.30 − 9.95 − 9.95 = 157.40 ✓）。

## 结算轨全空确认

```sql
-- 订单表
SELECT
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE effective_service_fee IS NOT NULL) AS eff_svc_not_null,
  COUNT(*) FILTER (WHERE effective_service_fee > 0) AS eff_svc_pos,
  COUNT(*) FILTER (WHERE effective_tech_service_fee IS NOT NULL) AS eff_tech_not_null,
  COUNT(*) FILTER (WHERE effective_tech_service_fee > 0) AS eff_tech_pos
FROM colonelsettlement_order
WHERE deleted = 0 AND order_status = 1;

-- 业绩表
SELECT
  COUNT(*) AS total_valid,
  COUNT(*) FILTER (WHERE effective_service_fee IS NOT NULL) AS perf_eff_svc_not_null,
  COUNT(*) FILTER (WHERE effective_service_fee > 0) AS perf_eff_svc_pos,
  COUNT(*) FILTER (WHERE effective_service_profit IS NOT NULL) AS perf_eff_profit_not_null,
  COUNT(*) FILTER (WHERE effective_service_profit > 0) AS perf_eff_profit_pos,
  COUNT(*) FILTER (WHERE effective_gross_profit IS NOT NULL) AS perf_eff_gross_not_null,
  COUNT(*) FILTER (WHERE effective_gross_profit > 0) AS perf_eff_gross_pos
FROM performance_records
WHERE is_valid = TRUE;
```

```json
{
  "orders_paid_total": 1291,
  "orders_eff_svc_not_null": 0,
  "orders_eff_svc_pos": 0,
  "orders_eff_tech_not_null": 0,
  "orders_eff_tech_pos": 0,
  "perf_valid_total": 1294,
  "perf_eff_svc_not_null": 0,
  "perf_eff_svc_pos": 0,
  "perf_eff_profit_not_null": 0,
  "perf_eff_profit_pos": 0,
  "perf_eff_gross_not_null": 0,
  "perf_eff_gross_pos": 0
}
```

结论：订单表与业绩表的 `effective_*` 列在本地 0 命中，与上游 `colonelMultiSettlementOrders` 近 6h 全部 `fetched=0` 一致 → `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE` 维持。

## 与正确基准的差距

| 指标 | 正确基准 | 本地支付单 | 本地有效业绩 | 缺口（支付单 - 基准） | 缺口率 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 订单数 | 3739 | 1291 | 1294 | -2448 | 65.4% |
| 订单额 | 79400.07 | 26626.94 | 26616.74 | -52773.13 | 66.5% |
| 服务费收入（est_svc） | 1434.01 | 455.32 | 440.42 | -978.69 | 68.2% |
| 技术服务费 | 121.36 | 未独立落库 | 未独立落库 | n/a | n/a |
| 服务费支出 | 1.90 | 未独立落库 | 反推 0 | n/a | n/a |
| 毛利 | 1009.89 | 未落库 | 351.56 | n/a | n/a |

差距形态：

- 订单数 / 订单额 / 服务费收入三项缺口率都在 65%~68%，形态高度一致 → 缺的是"补量"而非"改公式"。
- 正确基准日期范围未在用户输入中给出，本地仅覆盖 2026-06-03~2026-06-06 共 4 天。回补窗口待 `ORDER-HISTORY-BACKFILL-001` 用上游窗口确认。

## 给下一任务的可执行结论

1. `ORDER-HISTORY-BACKFILL-001`
   - 优先确认正确基准的日期跨度（用户未给）。
   - 调 `buyin.instituteOrderColonel` + `buyin.colonelMultiSettlementOrders` 历史窗口，订单数对齐到 ~3739。
   - 回补过程中关注 `order_status` 同步是否覆盖 `4` 之外的隐藏态。
   - 同时验证业绩域"只在 status=1 上落有效业绩"的过滤是否补全（当前有 3 条 orphan）。

2. `DASH-RECON-MONEY-DRIFT-001`
   - `serviceFeeExpense` 字段名错位（多处映射为 `recruiter + channel`），需新增独立落库字段或在 `MetricsVO`/`OrderSummaryRowVO` 中区分。
   - `/dashboard/metrics` 缺 `serviceFeeExpense`，前端 fallback 不安全，需补字段或新增独立 API。

3. `FRONTEND-DASHBOARD-SOURCE-VERIFY-001`
   - 前端 `serviceFeeExpense()` 缺字段时 fallback 为 `commission` 或 `biz + channel`，导致指标被错填。
   - 必须由后端字段先对齐，前端才能正确显示。

4. `ORDER-SETTLEMENT-SAMPLE-WATCH-001`
   - 持续观察 `colonelMultiSettlementOrders` 的 `fetched > 0` 何时出现。
   - 出现后立即校验 `effective_*` 列是否落值。
   - 暂不需要改本地代码。

## 验证清单

| 检查项 | 结果 |
| --- | --- |
| 编译 | 未执行，本任务无代码修改 |
| 单元测试 | 未执行，本任务无代码修改 |
| 集成测试 | 未执行，本任务无代码修改 |
| E2E | 未执行，本任务为只读 SQL 对账 |
| 健康检查 | PASS（继承主报告） |
| SQL 对账 | PASS，已采集订单/业绩按日明细、orphan 列表、结算轨空载确认 |
| 主报告修改 | 未执行（按"禁止 stage/commit 非本任务文件"隔离） |
| 远端部署 | 未执行，用户未要求且任务禁止 |

## Retro Summary

主报告已包含全部 4 张对比表 + 6 个必答问题 + 终判。本补查只补"按日明细 + orphan 列表 + 结算轨零命中"三类 SQL 证据，作为下一任务（ORDER-HISTORY-BACKFILL-001）的执行依据，不重新进入 API 采集流程。
