# ORDER-FIELD-MAPPING-AUDIT-001 订单字段映射与落库一致性只读审查

## 1. 结论

审查时间：2026-06-05 14:29:32 CST

环境：本地 `real-pre`

范围：只读审查；未修改 Java / Vue / SQL / Docker / env；未执行数据库写操作；未重启容器；未部署。

整体结论：`PARTIAL`

分项结论：

| 审查项 | 结论 | 证据 |
| --- | --- | --- |
| 6468 事实订单金额/预估字段映射 | `PASS` | 1132 单中 `order_amount` 与 raw `pay_goods_amount` 总和一致；可解析样本中 1027/1027 逐项匹配。 |
| 结算轨字段 | `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE` | raw `settled_goods_amount/settle_time/real_commission/settled_tech_service_fee` 均为 0 或空；DB `settle_amount/effective_*` 保持空/0。 |
| 金额单位 | `PASS` | DB 存分；API 展示元。`/data/orders/summary` 订单额 23252.28 = SQL `sum(order_amount)=2325228` 分。 |
| `pay_amount / settle_amount` 混用 | `PASS_ON_CURRENT_SAMPLE` | `settle_amount` 非零数为 0；未发现 `settle_amount = order_amount` 回填。 |
| `estimate_* / effective_*` 混用 | `PASS_ON_CURRENT_SAMPLE` | `effective_*` 非零数为 0；未发现 `effective_service_fee = estimate_service_fee` 回填。 |
| `pay_time` 是否被 `create_time` 回填 | `PASS_WITH_TIME_FACT_RISK` | `pay_time` 1132/1132 匹配 raw `pay_success_time`；但 raw 无 `create_time/order_create_time`，DB `order_create_time/create_time` 也等于 `pay_time`。 |
| `performance_records` 金额同步 | `PASS_ON_CURRENT_SAMPLE` | 1120 条业绩记录与订单表金额字段 1120/1120 匹配；结算轨为 0。 |
| 当前上游同步运行态 | `FAIL_UPSTREAM_SIGNATURE` | 2026-06-05 12:40 CST 起，6468 与 2704 均出现 `isv.signature-invalid`。 |

是否可以继续进入下一步看板口径修复：**不建议直接进入 `ORDER-FIELD-MAPPING-FIX-001` 或看板口径修复**。当前证据更支持先处理上游签名失败和结算样本缺失：

1. `DOUYIN-SIGNATURE-FAIL-AUDIT-001`：只读定位 2026-06-05 12:40 CST 起三方签名失败原因。
2. `ORDER-SETTLEMENT-SAMPLE-VERIFY-001`：签名恢复后等待/查找真实结算样本。
3. `ORDER-PERFORMANCE-LAG-AUDIT-001`：复核 12 条订单未生成 `performance_records` 的异步延迟或监听缺口。

## 2. 审查边界

本任务只读执行：

- 允许：代码读取、日志读取、内部 API GET/POST 登录取 token 后查询、PostgreSQL `SELECT`。
- 禁止且未执行：业务代码修改、SQL 写操作、migration、清库、重启容器、远端部署、git stage、git commit。

## 3. 工作区状态

审查前 `git status --short`：

```text
 M frontend/src/views/data/index.test.ts
 M frontend/src/views/data/index.vue
 M harness/HARNESS_CHANGELOG.md
 M harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md
```

分支与提交：

```text
branch: feature/auth-system
HEAD: cdc9031f fix(dashboard): add 30s short-TTL cache to /api/data/orders/summary
```

审查后状态：见本文第 11 节。

## 4. 代码链路证据

### 4.1 Gateway 上游响应解析

| 代码位置 | 证据 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java:95-109` | `listSettlement()` 调用 `buyin.colonelMultiSettlementOrders`，即 2704 结算源。 |
| `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java:126-137` | `listInstituteOrders()` 调用 `buyin.instituteOrderColonel`，即 6468 事实订单源。 |
| `RealDouyinOrderGateway.java:359-395` | raw Map 标准化为 `DouyinOrderItem`，金额取 `order_amount/total_amount/pay_amount/pay_goods_amount`，时间取 `create_time/order_create_time/pay_success_time`，结算时间取 `settle_time/update_time`。 |
| `RealDouyinOrderGateway.java:655-661` | 服务费从顶层或 `colonel_order_info.estimated_commission/real_commission` 解析。 |

### 4.2 订单同步映射

| 代码位置 | 证据 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java:621-630` | `SyncSource.INSTITUTE` 走 `applyInstituteFactToOrder()`，`SETTLEMENT` 走 `applyToOrder()`。 |
| `OrderSyncService.java:640-645` | `order_create_time/create_time` 从 raw 创建时间或 `itemCreateTime` 得到，`pay_time` 只从 raw `pay_success_time/pay_time` 得到。 |
| `OrderSyncService.java:646-648` | 仅非 INSTITUTE 来源才写 `settle_time`。 |
| `OrderSyncService.java:652` | raw JSON 保存到 `extra_data`。当前 DB 没有 `raw_payload` 列。 |

### 4.3 双轨金额解析器

| 代码位置 | 证据 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/service/OrderDualTrackAmountResolver.java:70-83` | `payAmount` 取 raw `pay_goods_amount/order_amount/...`；`settleAmount` 可从结算字段取，缺失时解析器内部可回退 `payAmount`。 |
| `OrderDualTrackAmountResolver.java:195-205` | 6468 事实源只写 `order_amount/actual_amount/estimate_*`，不写 `settle/effective`，避免污染结算轨。 |
| `OrderDualTrackAmountResolver.java:220-241` | 2704 结算源写 `settle/effective`，并兼容旧字段。 |
| `OrderSyncPersistenceService.java:145-152` | 按同步来源保护另一轨道：6468 更新时保留已有结算轨，2704 更新时保留已有预估轨。 |

### 4.4 Entity 与 Mapper

| 代码位置 | 证据 |
| --- | --- |
| `ColonelsettlementOrder.java:78-126` | `order_amount/actual_amount/settle_amount/estimate_* / effective_*` 均为 Long，注释单位为分。 |
| `ColonelsettlementOrder.java:379-398` | `pay_time/order_create_time/settle_time` 分列保存。 |
| `ColonelsettlementOrderMapper.xml:68-102` | insert 写入 `order_amount/actual_amount/settle_amount/estimate_* / effective_* / extra_data`。 |
| `ColonelsettlementOrderMapper.xml:105-154` | update 同步更新金额、时间和 `extra_data`，无额外 SQL 计算。 |

### 4.5 业绩与看板读取

| 代码位置 | 证据 |
| --- | --- |
| `PerformanceCalculationService.java:111-125` | 业绩记录直接从订单表复制 `pay/settle/estimate/effective` 字段。 |
| `PerformanceRecordMapper.xml:68-140` | `performance_records` upsert 按实体字段写入，无 SQL 回填。 |
| `PerformanceMetricsQueryService.java:78-138` | `/dashboard/metrics` 读取 `performance_records`，`create_time` 走预估轨，`settle_time` 走结算轨。 |
| `DataApplicationService.java:1546-1606` | `/data/orders/summary` 直接聚合订单表，按 timeField 选择预估/结算字段。 |
| `DataApplicationService.java:520-581` | `/data/orders/detail` 金额分转元；结算金额为空时前端展示可为空。 |

## 5. 表结构事实

当前 `colonelsettlement_order` 关键字段：

```text
order_amount bigint
actual_amount bigint
settle_amount bigint
estimate_service_fee bigint
effective_service_fee bigint
estimate_tech_service_fee bigint
effective_tech_service_fee bigint
pay_time timestamp
order_create_time timestamp
settle_time timestamp
order_status smallint
flow_point varchar
extra_data jsonb
```

字段名修正：

| 粘贴任务字段 | 当前 DB 实际字段 |
| --- | --- |
| `pay_amount` | `order_amount`（订单表）；`performance_records.pay_amount`（业绩表） |
| `raw_payload` | `extra_data` |

## 6. SQL 对账摘要

### 6.1 订单金额分布

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

### 6.2 混用检查

```text
same_order_settle_count=0
same_estimate_effective_fee_count=0
pay_time_eq_order_create_time_count=1132
pay_time_eq_local_create_time_count=1132
settle_time_null_count=1132
```

解释：

- `pay_time` 等于 raw `pay_success_time`，不是被 `create_time` 回填。
- raw 缺少 `create_time/order_create_time`，所以 `order_create_time/create_time` 退化为支付时间。该风险影响按创建时间分析的语义，但不证明支付时间字段错误。

### 6.3 raw 字段有效值

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

### 6.4 raw 与 DB 匹配计数

```text
order_id_matches_raw=1132/1132
product_matches_raw=1132/1132
colonel_buyin_matches_raw_top=1132/1132
activity_matches_raw_nested=1027/1132

order_amount_matches_raw_pay_goods=1027/1027
actual_amount_matches_raw_pay_goods=1027/1027
estimate_fee_matches_raw_estimated=1027/1027
estimate_tech_matches_raw_tech=1027/1027
pay_time_matches_raw_pay_success_string=1132/1132
```

`activity_matches_raw_nested=1027/1132` 的补充事实：

```text
raw_activity_nonempty=1027
db_activity_nonempty=1132
```

说明剩余 105 单 DB 活动 ID 不是来自 raw 嵌套 activity 字段，可能来自归因服务补齐；本审查未把它定为金额字段 P0。

### 6.5 订单状态分布

```text
order_status=1 count=1048
order_status=4 count=84
flow_point 均为空
```

当前代码 `OrderCommissionPolicy` 仅把 `order_status=4` 视为不计入业绩。由于 raw `flow_point` 没有落成有效值，本轮不能证明退款/失效/已结算多状态映射完整。

### 6.6 业绩表对账

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

结论：

- 当前运行态没有复现旧报告中的 `performance_records.settle_amount = pay_amount` 污染。
- 12 条订单未生成 `performance_records`，需要另查事件监听/重算延迟。

## 7. 内部 API 只读验证

登录：`POST /api/auth/login` 使用已知 real-pre 测试账号 `admin/admin123`，token 未输出。

### 7.1 `/api/data/orders/summary`

```text
createTime:
code=200
orderCount=1132
orderAmount=23252.28
serviceFeeIncome=390.11
techServiceFee=39.5

settleTime:
code=200
orderCount=0
orderAmount=0.0
serviceFeeIncome=0.0
techServiceFee=0.0
```

API 与 SQL 一致。

### 7.2 `/api/dashboard/metrics`

```text
settle:
metricsSource=performance_records
track=settleTime
totalOrders=0
totalAmount=0.0
serviceFeeIncome=0.0

estimate:
metricsSource=performance_records
track=createTime
todayOrderCount=175
todayGmv=4139.63
totalOrders=175
totalAmount=4139.63
serviceFeeIncome=66.99
techServiceFee=6.74
grossProfit=42.07
```

说明：`/dashboard/metrics` 是当日指标，不等于 `/data/orders/summary` 默认 30 天总量，不能直接横向比总额。

### 7.3 `/api/data/orders/detail`

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

API 与 SQL 一致。

## 8. 必须输出对账表

### 表1：上游 raw 字段 -> 本地解析字段 -> DB 字段 -> 结论

| 字段 | raw 字段 | 本地解析 | DB 字段 | 结论 |
| --- | --- | --- | --- | --- |
| 订单ID | `order_id` | `RealDouyinOrderGateway.toOrderItem()` -> `OrderSyncService.mapOrder()` | `order_id` | `PASS`，1132/1132 匹配。 |
| 商品ID | `product_id` | Gateway 取 `product_id/productId` | `product_id` | `PASS`，1132/1132 匹配。 |
| 活动ID | `colonel_order_info.activity_id` | 主要由归因服务/映射补齐 | `colonel_activity_id` | `PARTIAL`，raw 非空 1027，DB 非空 1132，1027/1027 raw 匹配。 |
| 推广者/达人ID | `talent_id/author_id` 等 | Gateway 候选解析 + `resolveTalentUid` | `talent_id` / extra_data | `PARTIAL`，本轮未做达人维度专项逐单对账。 |
| 合作方/店铺ID | `shop_id/merchant_id` | Gateway `merchant_id/shop_id` -> `parseMerchantId` | `shop_id` | `PASS_ON_CODE`，样本未发现异常。 |
| `pick_source` | `pick_source` | Gateway + normalizer | `pick_source` | `BLOCKED_BY_SAMPLE`，1132 单均为空；不能证明渠道归因闭环。 |
| `colonel_buyin_id` | `colonel_buyin_id` | `rawOrderInfoValue` | `colonel_buyin_id` | `PASS`，1132/1132 匹配。 |
| `pay_amount` | `pay_goods_amount` | `OrderDualTrackAmountResolver.resolve()` | `order_amount` | `PASS`，总和与逐项匹配。 |
| `settle_amount` | `settled_goods_amount` | 仅 SETTLEMENT 源写入 | `settle_amount` | `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`，raw 全 0，DB 空/0。 |
| `estimate_service_fee` | `colonel_order_info.estimated_commission` | `resolve()` | `estimate_service_fee` | `PASS`，1027/1027 可解析样本匹配；998 单非零。 |
| `effective_service_fee` | `colonel_order_info.real_commission` | 仅结算轨写入 | `effective_service_fee` | `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`，raw 全 0，DB 空/0。 |
| `estimate_tech_service_fee` | `colonel_order_info.tech_service_fee` | `resolve()` | `estimate_tech_service_fee` | `PASS`，1027/1027 可解析样本匹配；998 单非零。 |
| `effective_tech_service_fee` | `colonel_order_info.settled_tech_service_fee` | 仅结算轨写入 | `effective_tech_service_fee` | `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`，raw 全 0，DB 空/0。 |
| `pay_time` | `pay_success_time` | `rawDateTime()` | `pay_time` | `PASS`，1132/1132 匹配。 |
| `settle_time` | `settle_time` | 仅非 INSTITUTE 源写入 | `settle_time` | `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`，raw 全空/0，DB 全空。 |
| `order_create_time` | raw 无 `create_time/order_create_time` | Gateway 回退 `pay_success_time` | `order_create_time` | `PASS_WITH_TIME_FACT_RISK`，不是 pay_time 错，但创建时间事实退化。 |
| `order_status/flow_point` | `flow_point` | Gateway 映射 `order_status/status/flow_point` | `order_status/flow_point` | `PARTIAL`，当前只有 status 1/4，flow_point DB 为空。 |

### 表2：金额单位检查

| 字段 | raw 单位 | 本地转换方式 | DB 单位 | 是否疑似分/元错误 | 证据 |
| --- | --- | --- | --- | --- | --- |
| `order_amount` | 分 | Long 原样落库 | 分 | 否 | raw sum 2325228 = DB sum 2325228；API 展示 23252.28 元。 |
| `actual_amount` | 分 | 6468 分支设为 payAmount | 分 | 否 | DB sum 2325228；与 raw pay_goods_amount 一致。 |
| `settle_amount` | 分 | 2704 分支写入 | 分 | 无样本 | raw settled sum 0，DB 无非零。 |
| `estimate_service_fee` | 分 | Long 原样落库 | 分 | 否 | raw estimated sum 39011 = DB sum 39011。 |
| `effective_service_fee` | 分 | 2704 分支写入 | 分 | 无样本 | raw real_commission sum 0，DB 无非零。 |
| `estimate_tech_service_fee` | 分 | Long 原样落库 | 分 | 否 | raw tech sum 3950 = DB sum 3950。 |
| `effective_tech_service_fee` | 分 | 2704 分支写入 | 分 | 无样本 | raw settled tech sum 0，DB 无非零。 |

### 表3：逐单样本对账

| order_id | raw pay | DB pay | raw estimate fee | DB estimate fee | raw effective fee | DB effective fee | raw pay_time | DB pay_time | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| 6953437965679597401 | 3890 | 3890 |  | 0 |  |  | 2026-06-05 12:28:31 | 2026-06-05 12:28:31 | `PASS` |
| 6953435632943961553 | 2990 | 2990 | 30 | 30 | 0 |  | 2026-06-05 12:25:37 | 2026-06-05 12:25:37 | `PASS` |
| 6953435634202908077 | 999 | 999 |  | 0 |  |  | 2026-06-05 12:24:35 | 2026-06-05 12:24:35 | `PASS` |
| 6953439899489343231 | 1399 | 1399 |  | 0 |  |  | 2026-06-05 12:24:34 | 2026-06-05 12:24:34 | `PASS` |
| 6953433130388887346 | 1990 | 1990 | 60 | 60 | 0 |  | 2026-06-05 12:24:28 | 2026-06-05 12:24:28 | `PASS` |
| 6953435657510000325 | 1990 | 1990 | 40 | 40 | 0 |  | 2026-06-05 12:23:49 | 2026-06-05 12:23:49 | `PASS` |
| 6953435581626717704 | 990 | 990 |  | 0 |  |  | 2026-06-05 12:23:26 | 2026-06-05 12:23:26 | `PASS` |
| 6953435614590670084 | 1590 | 1590 | 48 | 48 | 0 |  | 2026-06-05 12:23:25 | 2026-06-05 12:23:25 | `PASS_CANCELLED_ZEROED_IN_PERF` |
| 6953435566679659788 | 2590 | 2590 | 26 | 26 | 0 |  | 2026-06-05 12:21:56 | 2026-06-05 12:21:56 | `PASS` |
| 6953437915736708986 | 1990 | 1990 | 20 | 20 | 0 |  | 2026-06-05 12:21:24 | 2026-06-05 12:21:24 | `PASS` |
| 6953437883665421384 | 2990 | 2990 | 30 | 30 | 0 |  | 2026-06-05 12:20:24 | 2026-06-05 12:20:24 | `PASS` |
| 6953435569265316934 | 1890 | 1890 | 38 | 38 | 0 |  | 2026-06-05 12:19:19 | 2026-06-05 12:19:19 | `PASS` |

### 表4：问题清单

| 级别 | 问题 | 证据 | 影响 | 下一步 |
| --- | --- | --- | --- | --- |
| P0 | 当前真实上游签名失败 | 2026-06-05 12:40 CST 起，6468/2704 订单接口连续 `isv.signature-invalid`；活动商品接口也出现同类错误。 | 阻塞继续同步新订单、结算样本和活动商品样本。 | `DOUYIN-SIGNATURE-FAIL-AUDIT-001`。 |
| P1 | 12 条订单未生成 `performance_records` | 订单 1132，业绩 1120，缺 12。 | `/dashboard/metrics` 读 performance_records，可能短期少算最新订单。 | `ORDER-PERFORMANCE-LAG-AUDIT-001`。 |
| P1 | `order_create_time/create_time` 与 `pay_time` 全等 | raw 没有 `create_time/order_create_time`，DB 1132/1132 全等。 | 按创建时间统计实际等价于支付时间；若业务需要真实下单时间，需上游字段样本或映射补充。 | `ORDER-TIME-FIELD-SOURCE-AUDIT-001`。 |
| P1 | 当前无真实结算样本 | raw 结算字段全 0，DB 结算轨全空/0。 | 不能证明 2704 结算轨字段映射在真实结算样本上通过。 | `ORDER-SETTLEMENT-SAMPLE-VERIFY-001`。 |
| P2 | `flow_point` 无有效落库 | DB `flow_point` 均为空，状态仅 1/4。 | 状态机细分能力不足，不能仅凭本轮样本证明退款/失效/已结算映射。 | 后续状态专项。 |
| P2 | `pick_source` 样本不足 | 1132 单 `pick_source` 均为空；mapping 13 条但订单未命中。 | 渠道归因闭环仍不能 PASS。 | 继续等待系统转链真实订单。 |

## 9. 阶段性结论链路

现象：

- real-pre 订单表存在真实订单 1132 条。
- 订单列表和 summary 有成交/预估数据，结算轨为 0。

证据：

- raw `pay_goods_amount` 与 DB `order_amount` 总和一致。
- raw `estimated_commission/tech_service_fee` 与 DB `estimate_*` 总和一致。
- raw 结算字段全为 0，DB `settle/effective` 为空/0。
- performance_records 与订单表金额字段 1120/1120 匹配。
- 12:40 CST 起真实上游签名失败。

推论：

- 当前已有样本不支持“金额单位错位”。
- 当前已有样本不支持“pay_amount / settle_amount 混用”。
- 当前已有样本不支持“estimate_* / effective_* 混用”。
- 当前已有样本不能证明结算轨映射 PASS，因为没有有效结算样本。
- 当前实时同步故障属于上游鉴权/签名层，不是字段映射层。

阶段性结论：

当前订单字段映射对 6468 事实/预估样本为 `PASS`；结算轨为 `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`；整体任务因当前上游签名失败、缺结算样本、12 条业绩未生成而标记 `PARTIAL`。

## 10. 未执行项

- 未执行构建：本任务无代码修改。
- 未重启容器：只读审查明确禁止。
- 未执行数据库写操作：仅 SELECT。
- 未执行远端部署：用户未要求，且本任务禁止部署。
- 未提交 / 未推送：任务明确禁止 stage/commit。

## 11. 审查后状态

审查后 `git status --short`：

```text
 M frontend/src/views/data/index.test.ts
 M frontend/src/views/data/index.vue
 M harness/HARNESS_CHANGELOG.md
 M harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md
?? harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
?? harness/reports/order-field-mapping-audit-001-20260605-142932.md
```

本任务新增文件清单：

```text
harness/reports/order-field-mapping-audit-001-20260605-142932.md
harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
```

格式检查：

```text
git diff --check -- harness/reports/order-field-mapping-audit-001-20260605-142932.md harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
PASS
```
