# Evidence — DASHBOARD-TODAY-SNAPSHOT-RECON-001

| 项 | 值 |
|---|---|
| snapshotAt | 2026-06-06 15:43:46 +08:00 |
| 环境 | local real-pre |
| 分支 | feature/auth-system @ 3fb1ebd0 |
| 工作区 | 只读审查，无代码变更 |
| 结论 | **PARTIAL** |

## 环境

```text
TimeZone (host): China Standard Time
TimeZone (postgres container): UTC
DB container: saas-active-postgres-real-pre-1
DB name: saas_real_pre
API: http://127.0.0.1:8081
全量订单 deleted=0: 11544
```

## SQL 证据（`SET TIME ZONE 'Asia/Shanghai'`）

### 1. 今天订单表 `pay_time` 汇总

```sql
SELECT count(*) AS order_count,
       sum(order_amount) AS order_amount_cent,
       sum(estimate_service_fee) AS estimate_service_fee_cent,
       sum(estimate_tech_service_fee) AS estimate_tech_service_fee_cent
FROM colonelsettlement_order
WHERE deleted = 0
  AND pay_time >= date_trunc('day', timestamp '2026-06-06 15:43:46')
  AND pay_time <= timestamp '2026-06-06 15:43:46';
```

结果：

```text
order_count=4709
order_amount_cent=9963133  → ¥99631.33
estimate_service_fee_cent=153920 → ¥1539.20
estimate_tech_service_fee_cent=15494 → ¥154.94
```

### 2. 今天业绩表（join `pay_time`）

全量：

```text
performance_count=4709, valid=4326, invalid=383, reversed=383
estimate_service_fee_cent=153920
estimate_service_profit_cent=127908 → ¥1279.08
estimate_recruiter_commission_cent=19153 → ¥191.53
estimate_channel_commission_cent=19153 → ¥191.53
estimate_gross_profit_cent=89602 → ¥896.02
```

valid only：

```text
valid_count=4326
pay_amount_cent=9150355 → ¥91503.55
estimate_service_fee_cent=142227 → ¥1422.27
estimate_tech_service_fee_cent=14319 → ¥143.19
inferred_expense_cent=0
talent_commission_cent=0
```

### 3. anti-join / 重复

```text
missing_performance=0
dup_performance_rows=0
```

### 4. 订单状态分布（今日 pay_time，Top）

```text
order_status | flow_point | cnt  | order_amount_cent | estimate_service_fee_cent
1            | (null)     | 4709 | 9963133           | 153920
```

（今日样本全部为 `order_status=1`，无效 383 单仍在 `performance_records` 标记 `is_valid=false`）

### 5. pay_time vs create_time

```text
pay_ne_create=0, pay_null=0
```

## API 证据

### `/api/orders`

```http
GET /api/orders?page=1&size=1&timeField=createTime&startTime=2026-06-06 00:00:00&endTime=2026-06-06 15:43:46
Authorization: Bearer <admin>
```

```json
{ "data": { "total": 4709 } }
```

### `/api/data/orders/summary`

```http
GET /api/data/orders/summary?timeField=createTime&startDate=2026-06-06&endDate=2026-06-06
```

```json
{
  "total": {
    "orderCount": 4709,
    "orderAmount": 99631.33,
    "serviceFeeIncome": 1539.20,
    "techServiceFee": 154.94,
    "serviceFeeExpense": 415.32,
    "serviceFeeProfit": 1384.26,
    "grossProfit": 968.94
  }
}
```

### `/api/dashboard/metrics`

```http
GET /api/dashboard/metrics?timeField=createTime
```

```json
{
  "estimate": {
    "totalOrders": 4326,
    "totalAmount": 91503.55,
    "serviceFeeIncome": 1422.27,
    "techServiceFee": 143.19,
    "serviceFee": 1279.08,
    "commission": 383.06,
    "bizCommission": 191.53,
    "channelCommission": 191.53,
    "grossProfit": 896.02,
    "serviceFeeExpense": null,
    "metricsSource": "performance_records"
  },
  "settle": { "totalOrders": 0, "totalAmount": 0.0 }
}
```

### `/api/performance/summary`

```http
GET /api/performance/summary?timeFilterType=pay&timeStart=2026-06-06T00:00:00&timeEnd=2026-06-06T23:59:59
```

```json
{
  "estimate": {
    "orderCount": 4326,
    "orderAmount": 9150355,
    "serviceFeeIncome": 142227,
    "techServiceFee": 14319,
    "serviceFeeProfit": 127908,
    "serviceFeeExpense": 38306,
    "recruiterCommission": 19153,
    "channelCommission": 19153,
    "grossProfit": 89602
  },
  "effective": { "orderCount": 0 }
}
```

## 代码证据（公式）

| 位置 | 行为 |
|------|------|
| `PerformanceSummaryService.mapTrackSummary` L308 | `serviceFeeExpense = recruiter + channel` |
| `DataApplicationService.toOrderSummaryRow` L2020 | `serviceFeeExpense = biz + channel` |
| `DataApplicationService.toOrderDetailVO` L581-583 | `estimateServiceFeeExpense = recruiter + channel` |
| `CommissionService.calculateByActivityBuckets` L248-271 | `serviceFeeNet = income - tech`；`gross = net - biz - channel` |
| `frontend/.../data/index.vue` L479-483 | 无 `serviceFeeExpense` 时 fallback `commission` |

## 构建 / 重启 / 部署

未执行（只读任务）。

## 最终判定

| 检查 | 结果 |
|------|------|
| 今日订单覆盖 | NEAR_PASS（4709 vs 4716） |
| 今日订单额 | NEAR_PASS |
| 今日服务费收入 | FAIL |
| 今日技术服务费 | PASS |
| 服务费支出公式 | FAIL |
| 服务费收益/毛利公式 | FAIL |
| 结算轨 | BLOCKED（无样本） |
| 建议下一步 | **DASH-RECON-MONEY-DRIFT-001** |
