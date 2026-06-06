# Evidence Report: ORDER-FIELD-MAPPING-AUDIT-001

## 基本信息

时间：2026-06-05 14:29:32 CST

环境：本地 `real-pre`

分支：`feature/auth-system`

HEAD：`cdc9031f fix(dashboard): add 30s short-TTL cache to /api/data/orders/summary`

Scope：只读审查 / reports-only

远端部署：未执行

结论：`PARTIAL`

## 工作区状态

审查前 dirty：

```text
 M frontend/src/views/data/index.test.ts
 M frontend/src/views/data/index.vue
 M harness/HARNESS_CHANGELOG.md
 M harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md
```

本任务新增文件：

```text
harness/reports/order-field-mapping-audit-001-20260605-142932.md
harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
```

审查后 dirty：

```text
 M frontend/src/views/data/index.test.ts
 M frontend/src/views/data/index.vue
 M harness/HARNESS_CHANGELOG.md
 M harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md
?? harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
?? harness/reports/order-field-mapping-audit-001-20260605-142932.md
```

格式检查：

```text
git diff --check -- harness/reports/order-field-mapping-audit-001-20260605-142932.md harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
PASS
```

## 安全检查

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

结果：

```text
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

## Docker 与健康检查

命令：

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Invoke-WebRequest http://127.0.0.1:8081/api/system/health
Invoke-WebRequest http://127.0.0.1:3001/healthz
```

结果：

```text
saas-active-frontend-real-pre-1   Up 3 hours (healthy)    0.0.0.0:3001->80/tcp
saas-active-backend-real-pre-1    Up 3 hours (healthy)    0.0.0.0:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 24 hours (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 3 days (healthy)     6379/tcp
backend health: {"status":"UP"}
frontend healthz: ok
```

## 代码证据

读取文件：

```text
backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java
backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
backend/src/main/java/com/colonel/saas/service/OrderDualTrackAmountResolver.java
backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java
backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml
backend/src/main/java/com/colonel/saas/service/PerformanceCalculationService.java
backend/src/main/resources/mapper/PerformanceRecordMapper.xml
backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
```

关键证据：

- 6468：`RealDouyinOrderGateway.listInstituteOrders()` -> `buyin.instituteOrderColonel`。
- 2704：`RealDouyinOrderGateway.listSettlement()` -> `buyin.colonelMultiSettlementOrders`。
- 6468 INSTITUTE 分支只写 `order_amount/actual_amount/estimate_*`，不写 `settle/effective`。
- 2704 SETTLEMENT 分支写 `settle/effective`。
- `extra_data` 是当前订单表 raw JSONB 字段，当前订单表没有 `raw_payload` 字段。

## SQL 证据

所有 SQL 均通过：

```powershell
docker exec -i saas-active-postgres-real-pre-1 sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" ...'
```

### 表结构

```text
colonelsettlement_order:
order_amount, actual_amount, settle_amount, estimate_service_fee,
effective_service_fee, estimate_tech_service_fee, effective_tech_service_fee,
colonel_buyin_id, colonel_activity_id, order_status, settle_time,
pick_source, create_time, extra_data, pay_time, order_create_time, flow_point

performance_records:
pay_amount, settle_amount, estimate_service_fee, effective_service_fee,
estimate_tech_service_fee, effective_tech_service_fee, order_status,
settle_time, order_create_time
```

### 订单表汇总

```text
total=1132
order_amount_nonzero=1132
actual_amount_nonzero=1132
settle_amount_nonzero=0
estimate_fee_nonzero=998
effective_fee_nonzero=0
estimate_tech_fee_nonzero=998
effective_tech_fee_nonzero=0
sum_order_amount=2325228
sum_actual_amount=2325228
sum_settle_amount=NULL
sum_estimate_service_fee=39011
sum_effective_service_fee=NULL
sum_estimate_tech_service_fee=3950
sum_effective_tech_service_fee=NULL
```

### raw 有效值

```text
raw_pay_goods_nonzero=1132
sum_raw_pay_goods=2325228
raw_settled_goods_nonzero=0
sum_raw_settled_goods=0
raw_settle_time_nonzero=0

raw_estimated_commission_nonzero=998
sum_raw_estimated_commission=39011
raw_real_commission_nonzero=0
sum_raw_real_commission=0
raw_tech_fee_nonzero=998
sum_raw_tech_fee=3950
raw_settled_tech_fee_nonzero=0
sum_raw_settled_tech_fee=0
```

### 混用与时间

```text
same_order_settle_count=0
same_estimate_effective_fee_count=0
pay_time_matches_raw_pay_success_string=1132/1132
order_create_eq_pay_time_count=1132/1132
local_create_eq_pay_time_count=1132/1132
settle_time_null_count=1132/1132
```

### 业绩表

```text
perf_total=1120
perf_pay_nonzero=1120
perf_settle_nonzero=0
perf_estimate_fee_nonzero=987
perf_effective_fee_nonzero=0
perf_pay_amount=2304519
perf_settle_amount=0
perf_estimate_service_fee=38653
perf_effective_service_fee=0

joined_total=1120
perf_pay_matches_order_amount=1120
perf_settle_matches_order_settle=1120
perf_est_fee_matches_order=1120
perf_eff_fee_matches_order=1120

orders_without_perf=12
orders_with_perf=1120
```

## API 证据

登录：

```text
POST /api/auth/login admin/admin123 -> code=200
```

token 未输出。

`GET /api/data/orders/summary?timeField=createTime`：

```text
code=200
orderCount=1132
orderAmount=23252.28
serviceFeeIncome=390.11
techServiceFee=39.5
```

`GET /api/data/orders/summary?timeField=settleTime`：

```text
code=200
orderCount=0
orderAmount=0.0
serviceFeeIncome=0.0
techServiceFee=0.0
```

`GET /api/dashboard/metrics`：

```text
settle.metricsSource=performance_records
settle.totalOrders=0
settle.totalAmount=0.0
settle.serviceFeeIncome=0.0

estimate.metricsSource=performance_records
estimate.todayOrderCount=175
estimate.todayGmv=4139.63
estimate.serviceFeeIncome=66.99
estimate.techServiceFee=6.74
estimate.grossProfit=42.07
```

`GET /api/data/orders/detail?page=1&size=5&timeField=createTime`：

```text
code=200
total=1132
records=5
firstOrderId=6953437965679597401
firstPayAmount=38.9
firstSettleAmount=null
firstEstimateServiceFee=0.0
firstEffectiveServiceFee=null
firstPayTime=2026-06-05T12:28:31
firstSettleTime=null
```

## 日志证据

最近订单同步日志：

```text
2026-06-05T04:30:44.758Z ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT ... fetched=100 inserted=29 updated=71 ... failed=0
2026-06-05T04:40:00.230Z Douyin API business error, method=buyin.instituteOrderColonel, code=40003, subCode=isv.signature-invalid
2026-06-05T04:40:00.276Z Douyin API business error, method=buyin.colonelMultiSettlementOrders, code=40003, subCode=isv.signature-invalid
2026-06-05T04:59:59.948Z OrderSyncJob.syncPayRecent failed
2026-06-05T06:20:01.160Z OrderSyncJob.syncInstituteOrdersRecent failed
```

换算为北京时间：

- 2026-06-05 12:30:44 CST：6468 仍成功。
- 2026-06-05 12:40:00 CST 起：6468 / 2704 连续签名失败。

同类签名失败也出现在活动商品接口 `alliance.colonelActivityProduct`，说明不是订单字段映射层的问题。

## 构建 / 重启 / 部署

构建：未执行，原因是只读审查且未改代码。

Docker 重启：未执行，原因是用户明确禁止重启容器。

健康检查：已执行，backend/frontend/container healthy。

业务验证：已执行 SQL 与只读 API 对账。

远端部署：未执行，用户未要求且任务禁止。

## 剩余风险

1. 当前上游签名失败阻塞新增真实样本。
2. 当前无有效结算样本，不能证明 2704 结算轨字段真实 PASS。
3. 12 条订单缺 `performance_records`，需另查异步计算/事件监听延迟。
4. `order_create_time/create_time` 全等于 `pay_time`，如果业务需要真实下单时间，需要上游字段或映射策略专项。
5. 1132 单 `pick_source` 为空，渠道归因闭环仍不能 PASS。

## 结论

`PARTIAL`

当前 6468 事实/预估字段映射与落库一致；结算轨因 raw 样本全 0 标记为 `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`；当前上游签名失败是独立 P0 运行态风险。
