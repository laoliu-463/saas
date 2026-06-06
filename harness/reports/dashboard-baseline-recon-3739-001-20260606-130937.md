# DASHBOARD-BASELINE-RECON-3739-001

## 任务边界

- 时间：2026-06-06 13:09 CST
- 环境：本地 `real-pre`
- 模式：只读审查
- 禁止项执行情况：未修改业务代码、未修改 SQL/migration、未写库、未重启容器、未部署远端、未 stage/commit
- Completion Gate：Gate 4 的只读诊断口径；本轮不进入修复和 E2E 改动

## 正确基准

用户确认以下数据为正确口径：

| 指标 | 预估/成交 | 结算 |
| --- | ---: | ---: |
| 订单数 | 3739 | 0 |
| 订单额 | 79400.07 | 0 |
| 服务费收入 | 1434.01 | 0 |
| 技术服务费 | 121.36 | 0 |
| 服务费支出 | 1.90 | 0 |
| 服务费收益 | 1310.75 | 0 |
| 招商提成 | 131.07 | 0 |
| 渠道/媒介提成 | 169.77 | 0 |
| 毛利 | 1009.89 | 0 |

正确基准可反推：

```text
服务费收益 = 服务费收入 - 技术服务费 - 服务费支出
1310.75 = 1434.01 - 121.36 - 1.90

毛利 ~= 服务费收益 - 招商提成 - 渠道/媒介提成
1009.89 ~= 1310.75 - 131.07 - 169.77
```

## 环境事实

| 项 | 结果 |
| --- | --- |
| repo | `D:/Projects/SAAS` |
| branch | `feature/auth-system` |
| commit | `98175caa docs(reports): record local real-pre upstream reconnect verification` |
| git dirty | 任务前已有 5 个 untracked report 文件 |
| safety-check | `real-pre full DryRun` PASS |
| Docker | backend / frontend / postgres / redis 均 healthy |
| 后端 health | `/api/system/health` 返回 `{"status":"UP"}` |
| 前端 health | `/healthz` 返回 `ok` |
| real-pre 开关 | `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`、`ORDER_SYNC_ENABLED=true` |
| 前端 API 指向 | Axios `baseURL=/api`；Nginx `/api/` 反代到 `${BACKEND_UPSTREAM}/api/`；compose 默认 `backend-real-pre:8080` |

## 表1：正确基准 vs 本地 DB / API

金额单位：元。DB 原始金额为分，已换算。

| 字段 | 正确基准 | 本地订单表 `colonelsettlement_order` | 本地 `performance_records` | `/api/data/orders/summary` | `/api/dashboard/metrics` |
| --- | ---: | ---: | ---: | ---: | ---: |
| 成交订单数 | 3739 | 1390 | 1390 总数 / 1293 valid | 1390 | 132 今日 |
| 结算订单数 | 0 | 0 | 0 | 0（`settleTime` 查询） | 0 |
| 成交订单额 | 79400.07 | 28606.10 | 28606.10 总数 / 26404.74 valid | 28606.10 | 2679.62 今日 |
| 结算订单额 | 0 | 0 | 0 | 0 | 0 |
| 服务费收入预估 | 1434.01 | 481.31 | 481.31 总数 / 440.30 valid | 481.31 | 42.30 今日 |
| 服务费收入结算 | 0 | 0 | 0 | 0 | 0 |
| 技术服务费预估 | 121.36 | 48.66 | 48.66 总数 / 44.54 valid | 48.66 | 4.25 今日 |
| 技术服务费结算 | 0 | 0 | 0 | 0 | 0 |
| 服务费支出预估 | 1.90 | 未存储 | valid SQL 反推为 0；接口映射为提成合计 119.06 | 129.76 | 字段缺失，前端 fallback 为提成合计 |
| 服务费支出结算 | 0 | 0 | 0 | 0 | 0 |
| 服务费收益预估 | 1310.75 | 未存储 | 395.76 valid | 432.65 | 38.05 今日 |
| 服务费收益结算 | 0 | 0 | 0 | 0 | 0 |
| 招商提成预估 | 131.07 | 未存储 | 59.53 valid | 包含在 `serviceFeeExpense` | 5.60 今日 |
| 招商提成结算 | 0 | 0 | 0 | 0 | 0 |
| 渠道/媒介提成预估 | 169.77 | 未存储 | 59.53 valid | 包含在 `serviceFeeExpense` | 5.60 今日 |
| 渠道/媒介提成结算 | 0 | 0 | 0 | 0 | 0 |
| 毛利预估 | 1009.89 | 未存储 | 276.70 valid | 302.89 | 26.85 今日 |
| 毛利结算 | 0 | 0 | 0 | 0 | 0 |

说明：

- `/api/dashboard/metrics` 当前实现无请求参数，按今日统计，不是全量 3739 对账接口。
- `/api/dashboard/summary` 是旧归因看板接口，本地返回 `orderCount=1293`、`orderAmount=0`、`serviceFee=0`，不适合与正确经营指标基准直接对账。

## 表2：订单数量差异定位

| 来源 | 订单数 | 与 3739 差异 | 可能原因 | 证据 |
| --- | ---: | ---: | --- | --- |
| 正确基准 | 3739 | 0 | 用户确认口径 | 用户输入 |
| `colonelsettlement_order` | 1390 | -2349 | 本地订单入库覆盖不足 | SQL `total_orders=1390` |
| `performance_records` | 1390 总数 / 1293 valid | -2349 / -2446 | 业绩表覆盖订单表，但 valid 会排除 reversed | SQL `missing_performance=0` |
| `/api/orders` | 1390 | -2349 | API 反映本地订单表 | API `total=1390` |
| `/api/orders/unattributed` | 1390 | -2349 | 当前订单全部未归因 | API `total=1390`；SQL `orders_with_pick_source=0` |
| `/api/data/orders/summary` | 1390 | -2349 | 汇总接口未额外过滤掉订单 | API `orderCount=1390` |
| `/api/performance/summary` | 1293 | -2446 | 只统计 valid 业绩 | API `estimate.orderCount=1293` |
| `/api/dashboard/metrics` | 132 今日 | 不可直接比较 | 该接口当前按今日窗口返回 | API `estimate.track=createTime`、`totalOrders=132` |

阶段性判断：

- 本地不是 3739 单的直接原因不是 dashboard 过滤，也不是 `performance_records` 缺行。
- 当前最可信分类是 `FAIL_LOCAL_ORDER_COVERAGE`：本地订单事实表只覆盖 1390 单。
- SQL 日期分布只覆盖 `2026-06-03` 至 `2026-06-06`：
  - 2026-06-03：325
  - 2026-06-04：608
  - 2026-06-05：317
  - 2026-06-06：140

## 同步范围证据

代码和运行日志显示自动同步更偏向近窗口，不是历史全量回补：

- `OrderSyncService` 常量：
  - `WINDOW_SECONDS=600`
  - `PAY_RECENT_WINDOW_SECONDS=6h`
  - `INSTITUTE_RECENT_WINDOW_SECONDS=24h`
  - `DEFAULT_COUNT=100`
  - `MAX_PAGES=200`
- `OrderSyncJob`：
  - `syncOrders()` 每 10 分钟普通增量
  - `syncPayRecent()` 每 30 分钟回扫近 6 小时
  - `syncInstituteOrdersRecent()` 每 10 分钟回扫近 24 小时
- 近 6 小时日志：
  - `buyin.instituteOrderColonel` 成功，通常 `pages=1 fetched=100 inserted=1~10 updated=90~99`
  - `buyin.colonelMultiSettlementOrders` 成功但 `pages=0 fetched=0`

因此，缺失 2349 单当前更符合“历史订单未回补 / 同步覆盖窗口不足 / 环境数据源未对齐”。具体缺失时间段需要下一任务用上游窗口或正确基准日期分布验证，当前不能凭空给最终根因。

## 表3：公式口径对比

| 指标 | 正确基准公式 | 本地代码公式 | 本地 SQL/API 反推 | 是否一致 | 证据 |
| --- | --- | --- | --- | --- | --- |
| 服务费收益 | `服务费收入 - 技术服务费 - 服务费支出` | `CommissionService`: `serviceFeeNet = serviceFeeIncome - techServiceFee` | valid：`440.30 - 44.54 - 395.76 = 0`，即服务费支出反推为 0 | 不一致 | `CommissionService.java:248-250` |
| 服务费支出 | 独立口径，正确基准为 1.90 | 多处映射为 `招商提成 + 渠道提成` | `/performance/summary=119.06`；`/data/orders/summary=129.76`；正确基准为 1.90 | 不一致 | `PerformanceSummaryService.java:290,308`；`DataApplicationService.java:1968` |
| 毛利 | `服务费收益 - 招商提成 - 渠道/媒介提成` | `grossProfit = serviceFeeNet - bizCommission - channelCommission` | valid：`395.76 - 59.53 - 59.53 = 276.70` | 只在“服务费支出=0”前提下一致 | `CommissionService.java:270-271` |
| `/dashboard/metrics.serviceFeeExpense` | 应有服务费支出双轨字段 | `MetricsVO` 无该字段 | 前端 `serviceFeeExpense()` 字段缺失时 fallback 到 `commission` 或 `biz+channel` | 不一致 | `MetricsVO.java:17-52`；`frontend/src/views/data/index.vue:479-482` |
| 逐单四舍五入误差 | 允许 0.01~0.05 | 本地不可验证正确基准 | 本地数据量不足，不能比较 1009.89 vs 1009.91 | 证据不足 | 当前 DB 仅 1390 单 |

## 表4：接口来源对比

| 接口 | 返回订单数 | 返回订单额 | 返回服务费收入 | 返回服务费支出 | 是否接近正确基准 | 结论 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `/api/dashboard/metrics` | 132 今日 | 2679.62 | 42.30 | 字段缺失，前端 fallback 为 11.20 | 否 | 今日经营指标，不是全量对账 |
| `/api/data/orders/summary` | 1390 | 28606.10 | 481.31 | 129.76 | 否 | 反映本地订单覆盖不足，并存在支出口径漂移 |
| `/api/data/orders/summary?timeField=settleTime` | 0 | 0 | 0 | 0 | 结算轨一致 | 当前无结算样本 |
| `/api/dashboard/summary` | 1293 | 0 | 0 | 不返回 | 否 | 旧归因看板/结算口径，不是经营指标矩阵 |
| `/api/orders` | 1390 | 不在摘要中返回 | 不返回 | 不返回 | 否 | 订单表事实入口 |
| `/api/orders/unattributed` | 1390 | 不在摘要中返回 | 不返回 | 不返回 | 否 | 当前全部未归因 |
| `/api/performance/summary` | 1293 | 26404.74 | 440.30 | 119.06 | 否 | 只统计 valid 业绩，支出口径为提成合计 |

## 必答问题

### 1. 本地为什么不是 3739 单？

阶段性结论：本地订单事实表只有 1390 单，且只覆盖 `2026-06-03 16:48:29` 到 `2026-06-06 12:56:35` 的支付时间；API 与 DB 一致，所以不是 dashboard 单独过滤。自动同步代码和日志显示当前自动任务主要跑 10 分钟、6 小时、24 小时近窗口，不是历史全量回补。

当前最可信原因是：本地订单同步/历史回补覆盖不足，缺 2349 单。缺失的确切上游时间段仍需 `ORDER-HISTORY-BACKFILL-001` 通过上游窗口或正确基准日期分布验证。

### 2. 如果本地已经有 3739 单，为什么 dashboard 不等于正确基准？

本地尚未有 3739 单，因此不能进入该分支作为主结论。现有证据显示即使数据补齐，仍需要修正 `serviceFeeExpense` 口径和 `/dashboard/metrics` 字段缺失问题。

### 3. `serviceFeeExpense` 本地口径是否错误？

相对用户确认的正确基准，是错误/漂移。

证据：

- 正确基准：服务费支出 = 1.90，不是招商提成 + 渠道提成。
- `PerformanceSummaryService.mapTrackSummary()` 明确 `serviceFeeExpense = recruiter + channel`。
- `DataApplicationService.toOrderSummaryRow()` 明确 `serviceFeeExpense = summary.bizCommission() + summary.channelCommission()`。
- `/dashboard/metrics` 后端 `MetricsVO` 不返回 `serviceFeeExpense`；前端缺字段时 fallback 为 `commission` 或 `bizCommission + channelCommission`。

### 4. 毛利差异是否只是逐单四舍五入？

对正确基准内部的 `1009.89` vs `1310.75 - 131.07 - 169.77 = 1009.91`，0.02 差异可以是逐单四舍五入造成。

但本地当前不能用“舍入误差”解释差异，因为本地数据量只有 1390 单，且本地毛利公式没有扣除正确基准中的独立服务费支出。

### 5. 结算为 0 是否仍属于上游样本阻塞？

当前证据支持 `BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE`：

- 订单表 `settle_time_non_null=0`、`settle_amount_nonzero=0`
- 业绩表 `settle_time_non_null=0`、`settle_amount_nonzero=0`
- 近 20 条订单样本 `settle_time=null`、`settle_amount=null`
- 近 6 小时 `buyin.colonelMultiSettlementOrders` 日志均 `fetched=0`

当前不能判定为本地结算逻辑 bug。

### 6. 下一步任务

优先级：

1. `ORDER-HISTORY-BACKFILL-001`
2. `DASH-RECON-MONEY-DRIFT-001`
3. `FRONTEND-DASHBOARD-SOURCE-VERIFY-001`
4. `ORDER-SETTLEMENT-SAMPLE-WATCH-001`

理由：

- 当前第一阻塞是本地订单覆盖不足，缺 2349 单。
- 数据补齐前，dashboard 数字无法与 3739 基准做最终对账。
- 但 `serviceFeeExpense` 口径漂移已经有代码/API 证据，应在订单覆盖补齐后立即修正。

## 结论

```text
结论：FAIL_LOCAL_ORDER_COVERAGE

主证据：
1. 本地订单表 1390 单，正确基准 3739 单，缺 2349 单。
2. performance_records 总数 1390，订单到业绩 anti-join=0，说明不是业绩表漏生成导致。
3. /api/orders 与 /api/data/orders/summary 均返回 1390，说明不是 dashboard 单独过滤导致。
4. 自动同步路径当前主要覆盖近窗口，未证明历史全量回补已执行。
5. serviceFeeExpense 多处仍等同于招商提成 + 渠道提成，与正确基准 1.90 不一致。
6. 结算轨为 0 有 DB 样本和上游日志支持，当前仍归类为上游结算样本阻塞。
```

## 本次无需 Harness 升级

本次为只读对账审查，未发现 Harness 执行入口、证据模板或安全规则需要升级。无需生成额外 Harness 改造任务。
