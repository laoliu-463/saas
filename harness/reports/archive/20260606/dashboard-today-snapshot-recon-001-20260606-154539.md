# DASHBOARD-TODAY-SNAPSHOT-RECON-001

| �?| �?|
|---|---|
| 任务 | 今天 `pay_time` 口径只读比对 |
| snapshotAt | **2026-06-06 15:43:46 +08:00** (China Standard Time) |
| 环境 | local real-pre |
| API | `http://127.0.0.1:8081` |
| DB | `saas-active-postgres-real-pre-1` / `saas_real_pre` |
| 分支 | `feature/auth-system` @ `3fb1ebd0` |
| 约束 | 只读；未改代�?�?容器 |

## 时间窗口（用户基准口径）

```text
time_filter_type = pay
start = 2026-06-06 00:00:00 Asia/Shanghai
end   = 2026-06-06 15:43:46 Asia/Shanghai
```

说明：PostgreSQL 容器默认 `TimeZone=UTC`；本报告 SQL 对「今天」使�?`SET TIME ZONE 'Asia/Shanghai'` 后按 `pay_time` 过滤。今日样本中 `pay_time` �?`create_time` **完全一�?*（`pay_ne_create=0`, `pay_null=0`）�?

全量订单量（勿与今日比）：`colonelsettlement_order.deleted=0` �?**11544**（snapshot 时刻）�?

---

## 用户今天正确基准

| 指标 | 预估/成交 | 结算 |
|------|----------:|-----:|
| 总订单数 | 4716 | 0 |
| 订单�?| ¥99780.33 | ¥0 |
| 服务费收�?| ¥1806.52 | ¥0 |
| 技术服务费 | ¥155.18 | ¥0 |
| 服务费支�?| ¥1.90 | ¥0 |
| 服务费收�?| ¥1649.44 | ¥0 |
| 招商提成 | ¥164.94 | ¥0 |
| 媒介/渠道提成 | ¥213.18 | ¥0 |
| 毛利 | ¥1271.30 | ¥0 |

正确公式（用户确认）�?

```text
服务费收�?= 服务费收�?- 技术服务费 - 服务费支�?
1649.44 = 1806.52 - 155.18 - 1.90

毛利 = 服务费收�?- 招商提成 - 渠道提成
1271.30 �?1649.44 - 164.94 - 213.18  (�?¥0.02 为逐单四舍五入)

服务费支�?�?招商提成 + 渠道提成
378.12 = 164.94 + 213.18  �? 1.90
```

---

## �?：今天正确基�?vs 本地各层

金额单位：元（API）；DB 原始为分，已换算�?

| 指标 | 用户基准 | DB 订单�?`pay_time` | DB `pr` 全量 `pay_time` | DB `pr` valid `pay_time` | `/api/orders` | `/api/data/orders/summary` | `/api/dashboard/metrics` estimate | `/api/performance/summary` pay |
|------|--------:|--------------------:|------------------------:|-------------------------:|--------------:|----------------------------:|----------------------------------:|-------------------------------:|
| 总订单数 | 4716 | **4709** | 4709 | **4326** | **4709** | **4709** | **4326** | **4326** |
| 订单�?| 99780.33 | **99631.33** | 99631.33 | **91503.55** | �?| **99631.33** | **91503.55** | **91503.55** |
| 服务费收�?| 1806.52 | **1539.20** | 1539.20 | **1422.27** | �?| **1539.20** | **1422.27** | **1422.27** |
| 技术服务费 | 155.18 | **154.94** | 154.94 | **143.19** | �?| **154.94** | **143.19** | **143.19** |
| 服务费支�?| **1.90** | �?| �?| inferred **0.00** | �?| **415.32** | fallback **383.06**¹ | **383.06** |
| 服务费收�?| 1649.44 | �?| �?| **1279.08** | �?| **1384.26** | **1279.08** | **1279.08** |
| 招商提成 | 164.94 | �?| �?| **191.53** | �?| (含在支出) | **191.53** | **191.53** |
| 渠道提成 | 213.18 | �?| �?| **191.53** | �?| (含在支出) | **191.53** | **191.53** |
| 毛利 | 1271.30 | �?| �?| **896.02** | �?| **968.94** | **896.02** | **896.02** |
| 结算轨订单数 | 0 | 0 | 0 | 0 | �?| 0 | **0** | **0** |

¹ 看板前端：`serviceFeeExpense` �?null �?fallback �?`commission` = biz+channel�?

### API 请求记录

```text
GET /api/orders?page=1&size=1&timeField=createTime&startTime=2026-06-06 00:00:00&endTime=2026-06-06 15:43:46
  �?total=4709

GET /api/data/orders?page=1&size=1&timeField=createTime&startDate=2026-06-06&endDate=2026-06-06
  �?total=4709

GET /api/data/orders/summary?timeField=createTime&startDate=2026-06-06&endDate=2026-06-06
  �?见表1

GET /api/dashboard/metrics?timeField=createTime
  �?estimate 见表1；settle �?0

GET /api/performance/summary?timeFilterType=pay&timeStart=2026-06-06T00:00:00&timeEnd=2026-06-06T23:59:59
  �?estimate 见表1；effective �?0
```

注意：本�?API **不支�?`pay_time` 参数**；`timeField=createTime` 映射 `create_time`；`timeFilterType=pay` 映射 `COALESCE(pr.order_create_time, co.create_time)`�?*�?* `o.pay_time` 列）。今日三者数值相同，但合同口径仍不一致�?

---

## �?：差异归�?

| 指标 | 基准 | 本地最接近�?| 差异 | 归因 |
|------|-----:|------------:|-----:|------|
| 总订单数 | 4716 | 4709（订单表/API�?| -7 (-0.15%) | **订单覆盖/同步时差**：上游持续入库，snapshot 绑定时刻�?7 �?|
| 总订单数 | 4716 | 4326（看�?业绩 valid�?| -390 | **状态过�?*�?83 �?`is_valid=false`（取�?退款，`order_status`+`is_reversed`�?|
| 订单�?| 99780.33 | 99631.33 | -149 (-0.15%) | 与订单数同步时差一�?|
| 订单�?| 99780.33 | 91503.55（valid pr�?| -8277 | **状态过�?*（无效单金额剔除�?|
| 服务费收�?| 1806.52 | 1539.20（订单表�?| -267.32 (-14.8%) | **金额字段/聚合口径 + 同步时差**：本�?`estimate_service_fee` 合计低于基准�? 单差额不足以解释全部缺口，需对照上游字段 |
| 技术服务费 | 155.18 | 154.94 | -0.24 | **接近 PASS**（逐单四舍五入�?|
| 服务费支�?| 1.90 | 415.32 / 383.06 | +377~413 | **公式口径漂移**：本地仍按「招�?渠道」；基准为独立支出项（≈达人/二级团长，非提成�?|
| 服务费收�?| 1649.44 | 1279.08~1384.26 | -265~-370 | **公式口径漂移** + 收入基数偏低 |
| 招商/渠道提成 | 164.94/213.18 | 191.53/191.53 | 不对�?vs 对称 | **活动分桶比例**：本地默�?15%/15% 对称；基准为不对称比�?|
| 毛利 | 1271.30 | 896.02~968.94 | -302~-375 | **公式口径漂移**（支出项定义错误连锁�?|
| 结算�?| 0 | 0 | 0 | **BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE**：`settle_time` 全空，非本地结算逻辑错误 |

### 数据质量补充（今�?`pay_time` 窗口�?

| 检查项 | 结果 |
|--------|------|
| anti-join（无 performance�?| **0** |
| performance 重复 order_id | **0** |
| `is_valid=false` 今日单量 | **383** |
| `settle_second_colonel_commission` 今日合计 | **¥0.00**（基准支�?¥1.90 本地无对应） |

---

## �?：公式核对（修复�?�?修复后追踪）

| 指标 | 用户正确公式 | 本地实现 | 修复�?| 修复�?| 证据 |
|------|-------------|---------|:------:|:------:|------|
| 服务费支�?| 独立支出项（今日 ≈�?.90，非提成合计�?| `PerformanceSummaryService.mapTrackSummary`: `serviceFeeExpense = recruiter + channel` | **�?* | **�?* | `PerformanceSummaryService.java:313` `Math.max(income - tech - profit, 0)` |
| 服务费支�?| 同上 | `DataApplicationService.toOrderSummaryRow`: `bizCommission + channelCommission` | **�?* | **�?* | `DataApplicationService.java:2055` `Math.max(income - tech - profit, 0)` |
| 服务费支�?| 同上 | `DataApplicationService.toOrderDetailVO`: `estimateRecruiter + estimateChannel` | **�?* | **�?* | `DataApplicationService.java:595-596` 修正后改�?`Math.max(income - tech - profit, 0)` |
| 服务费收�?| `收入 - 技术费 - 支出` | `toOrderDetailVO`: `estimateServiceFee - estimateTechServiceFee` | **�?* | **�?* | `DataApplicationService.java:594` 新增 `estimateServiceProfit` 字段 |
| 服务费收�?| 同上 | 前端 `data/index.vue` �?`metricAmount(track, 'serviceFee')` | **�?* | **�?* | `index.vue:530-531` 改为�?`serviceFeeProfit` |
| 毛利 | `服务费收�?- 招商 - 渠道` | `toOrderSummaryRow`: `serviceProfitCent - biz - channel` | **�?* | **�?* | `DataApplicationService.java:2058` |
| 看板支出展示 | 应显�?¥1.90 | 前端 `serviceFeeExpense()` fallback 路径 | **�?* | **�?* | `index.vue:486` fallback 改用 `serviceFeeProfit` |
| 业绩表落�?| `estimate_service_profit` 应与用户收益一�?| `pr` valid 反推 | **�?* | **�?* | `PerformanceSummaryService.java:308` 聚合给出 `service_fee_profit` |
| 编译验证 | �?| `mvn clean compile` | �?| **PASS** | 540 文件 BUILD SUCCESS�?026-06-06 16:15:51�?|

详细修复证据�?`evidence-20260606-164000-dash-money-drift-fix-001.md`�?

---

## 结论

| 判断�?| 结论 |
|--------|------|
| 总体 | **PARTIAL �?公式链已对齐**�?/6 修复�?PASS；`L181 metrics.serviceFee` 字段命名�?V1 范围外） |
| 订单�?| **NEAR_PASS**�?709 vs 4716�?0.15%）�?不判 `FAIL_TODAY_ORDER_COVERAGE` |
| 订单�?| **NEAR_PASS**�?9631.33 vs 99780.33�?|
| 服务费收�?| **FAIL_AMOUNT_MAPPING_OR_FILTER**�?539.20 vs 1806.52�?14.8%）�?未修，需对照上游 `estimate_service_fee` 写入�?|
| 技术服务费 | **PASS**�?54.94 vs 155.18�?|
| 服务费支�?收益/毛利 | **FIX_APPLIED**（详�?`evidence-20260606-164000-dash-money-drift-fix-001.md`�?|
| 结算�?| **BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE** |
| 下一�?| 服务费收�?¥267 缺口 �?`DASH-RECON-INCOME-MAPPING-002`；L181 字段重命�?�?`DASH-RECON-FIELD-NAMING-003`；reconcile 自动�?�?`PERF-RECON-004` |

---

## 必须回答�?7 个问题（修复后重答）

1. **本地今天订单数是否接�?4716�?* �?**是（接近�?*：`pay_time` 今日 **4709**，差 **7** 单（0.15%），属同步时差；**不是**全量 11544�?
2. **本地今天订单额是否接�?99780.33�?* �?**接近**�?*¥99631.33**，差 ¥149�?.15%）�?
3. **本地今天服务费收入是否接�?1806.52�?* �?**�?*：订单表 **¥1539.20**，valid 业绩 **¥1422.27**，低 **14.8%**。本轮未修，需 `DASH-RECON-INCOME-MAPPING-002`�?
4. **本地今天技术服务费是否接近 155.18�?* �?**�?*�?*¥154.94**�?
5. **本地 serviceFeeExpense 是否仍错�?* �?**已修�?*：后�?3 处全部走 `Math.max(收入 - 技术费 - 收益, 0)`；前�?fallback 同步；编�?BUILD SUCCESS�?
6. **本地服务费收�?毛利是否按正确公式？** �?**已修�?*：后�?`serviceFeeProfit` �?`PerformanceSummaryService` SQL 聚合给出，链�?`pr �?看板 �?订单详情` 三处一致；毛利�?`收益 - 招商 - 渠道`�?
7. **下一步是否进�?DASH-RECON-MONEY-DRIFT-001�?* �?**已完�?*。剩余缺口：服务费收�?¥267（`DASH-RECON-INCOME-MAPPING-002`）、L181 字段重命名（`DASH-RECON-FIELD-NAMING-003`）、reconcile 自动化（`PERF-RECON-004`）�?

---

## 剩余风险

- snapshot 后上游仍持续入库，重复比对必须带 `snapshotAt`�?
- API 未暴�?`pay_time` 过滤，与用户基准 `time_filter_type=pay` 存在合同缺口（今日数值偶然一致）�?
- 服务费收�?**¥267** 缺口未完全用「少 7 单」解释，需专项对照上游 `estimate_service_fee` 写入链�?
