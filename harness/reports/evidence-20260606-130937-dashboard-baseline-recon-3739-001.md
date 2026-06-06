# Evidence: DASHBOARD-BASELINE-RECON-3739-001

## 范围

- 环境：本地 `real-pre`
- 模式：只读
- 结论：`FAIL_LOCAL_ORDER_COVERAGE`
- 远端部署：未执行
- 数据库写操作：未执行
- 容器重启：未执行
- 业务代码修改：未执行

## Git / 环境

```text
git rev-parse --show-toplevel
D:/Projects/SAAS

git branch --show-current
feature/auth-system

git log -1 --oneline
98175caa docs(reports): record local real-pre upstream reconnect verification

git status --short
?? harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
?? harness/reports/evidence-20260606-121756-order-performance-backfill-001.md
?? harness/reports/evidence-20260606-122912.md
?? harness/reports/order-field-mapping-audit-001-20260605-142932.md
?? harness/reports/retro-20260606-122926.md
```

任务前已有 5 个 untracked report 文件。本任务未 stage/commit。

## Safety Check

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope full -DryRun

Safety check passed.
Secret presence only:
- DB_PASSWORD: present
- REDIS_PASSWORD: present
- JWT_SECRET: present
- DOUYIN_CLIENT_SECRET: present
- LOGISTICS_KD100_KEY: present
- TALENT_PROFILE_HTTP_TOKEN: missing
- TALENT_PROFILE_HTTP_AUTHORIZATION: missing
```

未输出密钥值。

## Docker / Health

```text
docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml ps

saas-active-backend-real-pre-1    Up 57 minutes (healthy)   0.0.0.0:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 26 hours (healthy)     0.0.0.0:3001->80/tcp
saas-active-postgres-real-pre-1   Up 47 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      Up 21 hours (healthy)     6379/tcp
```

```text
GET http://127.0.0.1:8081/api/system/health
{"status":"UP"}

GET http://127.0.0.1:3001/healthz
ok
```

运行态开关：

```text
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
ORDER_SYNC_ENABLED=true
```

## 前端 API 指向

```text
frontend/src/utils/request.ts:16-18
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

frontend/nginx/default.conf.template:28-30
location /api/ {
    limit_req zone=api burst=30 nodelay;
    proxy_pass http://${BACKEND_UPSTREAM}/api/;
}

docker-compose.real-pre.yml
BACKEND_UPSTREAM: ${BACKEND_UPSTREAM:-backend-real-pre:8080}
```

## SQL 证据

### 表结构

`colonelsettlement_order` 63 列，`performance_records` 38 列。关键字段：

```text
colonelsettlement_order.order_amount bigint
colonelsettlement_order.settle_amount bigint
colonelsettlement_order.estimate_service_fee bigint
colonelsettlement_order.effective_service_fee bigint
colonelsettlement_order.estimate_tech_service_fee bigint
colonelsettlement_order.effective_tech_service_fee bigint
colonelsettlement_order.pay_time timestamp
colonelsettlement_order.settle_time timestamp
colonelsettlement_order.deleted smallint

performance_records.pay_amount bigint
performance_records.settle_amount bigint
performance_records.estimate_service_fee bigint
performance_records.effective_service_fee bigint
performance_records.estimate_service_profit bigint
performance_records.effective_service_profit bigint
performance_records.estimate_recruiter_commission bigint
performance_records.estimate_channel_commission bigint
performance_records.estimate_gross_profit bigint
performance_records.is_valid boolean
performance_records.is_reversed boolean
```

### 订单表总量

```json
{
  "total_orders": 1390,
  "non_deleted_orders": 1390,
  "pay_time_non_null": 1390,
  "order_amount_nonzero": 1390,
  "settle_time_non_null": 0,
  "settle_amount_nonzero": 0,
  "sum_order_amount": 2860610,
  "sum_settle_amount": 0,
  "sum_estimate_service_fee": 48131,
  "sum_effective_service_fee": 0,
  "sum_estimate_tech_service_fee": 4866,
  "sum_effective_tech_service_fee": 0,
  "min_pay_time": "2026-06-03T16:48:29",
  "max_pay_time": "2026-06-06T12:56:35"
}
```

### 订单状态分布

```json
[
  { "order_status": 1, "flow_point": null, "cnt": 1290, "order_amount": 2631504, "estimate_service_fee": 43920 },
  { "order_status": 4, "flow_point": null, "cnt": 100, "order_amount": 229106, "estimate_service_fee": 4211 }
]
```

### 日期分布

```json
[
  { "pay_date": "2026-06-06", "cnt": 140, "order_amount": 290782, "estimate_service_fee": 4425 },
  { "pay_date": "2026-06-05", "cnt": 317, "order_amount": 704244, "estimate_service_fee": 12277 },
  { "pay_date": "2026-06-04", "cnt": 608, "order_amount": 1244872, "estimate_service_fee": 20744 },
  { "pay_date": "2026-06-03", "cnt": 325, "order_amount": 620712, "estimate_service_fee": 10685 }
]
```

### 业绩表总量

```json
{
  "perf_total": 1390,
  "valid_total": 1293,
  "reversed_total": 97,
  "settle_time_non_null": 0,
  "settle_amount_nonzero": 0,
  "sum_pay_amount": 2860610,
  "sum_settle_amount": 0,
  "sum_estimate_service_fee": 48131,
  "sum_effective_service_fee": 0,
  "sum_estimate_tech_service_fee": 4866,
  "sum_effective_tech_service_fee": 0,
  "sum_estimate_service_profit": 39576,
  "sum_effective_service_profit": 0,
  "sum_estimate_recruiter_commission": 5953,
  "sum_effective_recruiter_commission": 0,
  "sum_estimate_channel_commission": 5953,
  "sum_effective_channel_commission": 0,
  "sum_estimate_gross_profit": 27670,
  "sum_effective_gross_profit": 0
}
```

### anti-join / 重复

```json
{
  "missing_performance": 0,
  "performance_without_order": 0,
  "duplicate_performance": null
}
```

### 公式复核

`performance_records WHERE is_valid=true`：

```json
{
  "service_fee_income": 44030,
  "tech_fee": 4454,
  "service_profit": 39576,
  "recruiter_commission": 5953,
  "channel_commission": 5953,
  "gross_profit": 27670,
  "inferred_service_fee_expense": 0,
  "commission_as_expense": 11906,
  "aggregate_profit_minus_commissions": 27670
}
```

说明：

- 本地业绩表按 `服务费收入 - 技术服务费 = 服务费收益`，反推出的独立服务费支出为 0。
- 若把招商提成 + 渠道提成当作服务费支出，则为 119.06 元。
- 与正确基准 `服务费支出=1.90` 不一致。

### 归因与映射

```json
{
  "orders_with_pick_source": 0,
  "orders_with_channel": 0,
  "pick_source_mapping_total": 13,
  "pick_source_mapping_with_pick_source": 13
}
```

## API 证据

管理员登录仅用于获取 Bearer token；未输出 token。

### `/api/dashboard/metrics`

```json
{
  "estimate": {
    "totalOrders": 132,
    "todayOrderCount": 132,
    "totalAmount": 2679.62,
    "todayGmv": 2679.62,
    "serviceFee": 38.05,
    "commission": 11.2,
    "serviceFeeIncome": 42.3,
    "techServiceFee": 4.25,
    "talentCommission": 0.0,
    "bizCommission": 5.6,
    "channelCommission": 5.6,
    "grossProfit": 26.85,
    "metricsSource": "performance_records",
    "amountTrack": "estimate",
    "track": "createTime"
  },
  "settle": {
    "totalOrders": 0,
    "totalAmount": 0.0,
    "serviceFee": 0.0,
    "commission": 0.0,
    "serviceFeeIncome": 0.0,
    "techServiceFee": 0.0,
    "bizCommission": 0.0,
    "channelCommission": 0.0,
    "grossProfit": 0.0,
    "metricsSource": "performance_records",
    "amountTrack": "effective",
    "track": "settleTime"
  }
}
```

字段名检查：

```json
{
  "estimateFieldNames": [
    "todayOrderCount",
    "todayGmv",
    "pendingShipCount",
    "trend7d",
    "totalOrders",
    "totalAmount",
    "serviceFee",
    "commission",
    "serviceFeeIncome",
    "techServiceFee",
    "talentCommission",
    "bizCommission",
    "channelCommission",
    "grossProfit",
    "amountTrack",
    "metricsSource",
    "track"
  ]
}
```

`serviceFeeExpense` 字段不存在。

### `/api/data/orders/summary`

```json
{
  "orderCount": 1390,
  "orderAmount": 28606.1,
  "serviceFeeIncome": 481.31,
  "techServiceFee": 48.66,
  "serviceFeeExpense": 129.76,
  "serviceFeeProfit": 432.65,
  "grossProfit": 302.89,
  "recordsCount": 4
}
```

### `/api/data/orders/summary?timeField=settleTime&startDate=2026-06-03&endDate=2026-06-06`

```json
{
  "orderCount": 0,
  "orderAmount": 0.0,
  "serviceFeeIncome": 0.0,
  "techServiceFee": 0.0,
  "serviceFeeExpense": 0.0,
  "serviceFeeProfit": 0.0,
  "grossProfit": 0.0
}
```

### `/api/dashboard/summary`

```json
{
  "orderCount": 1293,
  "orderAmount": 0,
  "serviceFee": 0
}
```

### `/api/orders`

```json
{
  "total": 1390,
  "recordsCount": 1
}
```

### `/api/orders/unattributed`

```json
{
  "total": 1390,
  "recordsCount": 1
}
```

### `/api/performance/summary`

```json
{
  "estimate": {
    "orderCount": 1293,
    "orderAmount": 2640474,
    "serviceFeeIncome": 44030,
    "techServiceFee": 4454,
    "serviceFeeProfit": 39576,
    "serviceFeeExpense": 11906,
    "recruiterCommission": 5953,
    "channelCommission": 5953,
    "grossProfit": 27670
  },
  "effective": {
    "orderCount": 0,
    "orderAmount": 0,
    "serviceFeeIncome": 0,
    "techServiceFee": 0,
    "serviceFeeProfit": 0,
    "serviceFeeExpense": 0,
    "recruiterCommission": 0,
    "channelCommission": 0,
    "grossProfit": 0
  }
}
```

## 代码证据

### `/dashboard/metrics`

```text
DataController.java:174-180
@GetMapping("/dashboard/metrics")
public ApiResult<DualTrackMetricsVO> getMetrics(...)

DataApplicationService.java:875-903
if (performanceMetricsQueryService.hasPerformanceRecords()) {
  aggregate = performanceMetricsQueryService.aggregateRange(...)
  metrics.setMetricsSource("performance_records")
  metrics.setServiceFeeIncome(...)
  metrics.setTechServiceFee(...)
  metrics.setServiceFee(...)
  metrics.setBizCommission(...)
  metrics.setChannelCommission(...)
  metrics.setCommission(biz + channel)
  metrics.setGrossProfit(...)
}
```

`MetricsVO.java:17-52` 无 `serviceFeeExpense` 字段。

### `/data/orders/summary`

```text
DataApplicationService.java:1966-1970
vo.setServiceFeeIncome(centToYuan(serviceFeeIncome));
vo.setTechServiceFee(centToYuan(asLong(row, "tech_service_fee_cent")));
vo.setServiceFeeExpense(centToYuan(summary.bizCommission() + summary.channelCommission()));
vo.setServiceFeeProfit(centToYuan(summary.serviceFeeNet()));
vo.setGrossProfit(centToYuan(summary.grossProfit()));
```

### `/performance/summary`

```text
PerformanceSummaryService.java:290
将 recruiter_commission + channel_commission 合计为 service_fee_expense

PerformanceSummaryService.java:297-309
long recruiter = asLong(row.get("recruiter_commission"));
long channel = asLong(row.get("channel_commission"));
track.setServiceFeeExpense(recruiter + channel);
```

### 业绩计算

```text
CommissionService.java:248-250
serviceFeeNet = serviceFeeIncome - techServiceFee

CommissionService.java:267-271
bizCommission += ...
channelCommission += ...
grossProfit = serviceFeeNet - bizCommission - channelCommission
```

### 前端 fallback

```text
frontend/src/views/data/index.vue:479-482
const serviceFeeExpense = (track) => {
  const explicit = toNumber(track?.serviceFeeExpense)
  if (explicit > 0) return formatMoney(explicit)
  return formatMoney(toNumber(track?.commission) || toNumber(track?.bizCommission) + toNumber(track?.channelCommission))
}
```

## 同步日志证据

近 6 小时后端日志摘录：

```text
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=10 updated=90 failed=0
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=7 updated=93 failed=0
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=9 updated=91 failed=0
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=5 updated=95 failed=0
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL pages=0 fetched=0 inserted=0 updated=0 failed=0
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=PAY_RECENT pages=0 fetched=0 inserted=0 updated=0 failed=0
```

同步代码：

```text
OrderSyncService.java:53-58
WINDOW_SECONDS = 600
PAY_RECENT_WINDOW_SECONDS = 6h
INSTITUTE_RECENT_WINDOW_SECONDS = 24h

OrderSyncJob.java:64-65
syncOrders cron = 0 */10 * * * ?

OrderSyncJob.java:94-95
syncPayRecent cron = 0 */30 * * * ?

OrderSyncJob.java:124-125
syncInstituteOrdersRecent cron = 0 */10 * * * ?
```

## 验证清单

| 检查项 | 结果 |
| --- | --- |
| 编译 | 未执行，本任务无代码修改 |
| 单元测试 | 未执行，本任务无代码修改 |
| 集成测试 | 未执行，本任务无代码修改 |
| E2E | 未执行，本任务为只读 API/SQL 对账 |
| 健康检查 | PASS |
| API 对账 | PASS，已采集核心字段 |
| DB 对账 | PASS，已采集订单/业绩/公式/结算样本 |
| 代码口径检查 | PASS，已定位关键文件和行号 |
| Docker 重启 | 未执行，用户禁止重启且无代码修改 |
| 远端部署 | 未执行，用户未要求且任务禁止 |

## 剩余风险

- 缺失的 2349 单只定位到“本地未入库/未回补”，还未用上游正确基准日期分布或上游 API 历史窗口证明具体缺失区间。
- `serviceFeeExpense=1.90` 的字段来源尚未在本地 DB 中定位到独立落库字段；后续需明确上游字段、订单域落库字段和业绩域消费字段。
- 当前 `/dashboard/metrics` 是今日窗口，不支持传入全量时间范围；后续若要让看板与 3739 基准对齐，需要决定该接口是否应支持时间范围。

## Retro Summary

本次为只读对账审查，无需 Harness 升级。建议下一任务先执行 `ORDER-HISTORY-BACKFILL-001`，在本地订单覆盖达到 3739 后再执行 `DASH-RECON-MONEY-DRIFT-001`。
