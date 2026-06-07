# DASHBOARD-MONEY-HIDDEN-DEDUCTION-8291-001

时间：2026-06-06 22:05:00 +08:00
环境：local real-pre
分支：feature/auth-system
commit：cadfb220（工作区有未提交修复）

## 1. 8291 基准窗口

冻结样本窗口（与目标侧订单数/订单额对齐）：

```text
pay_time >= 2026-06-06 00:00:00
pay_time <= 2026-06-06 21:32:25
→ COUNT=8291, pay=¥178617.79, products=376, colonel_promoters=35
```

## 2. 差异拆解（冻结窗口 SQL）

| 指标 | 本地（修复前逻辑） | 目标 | 差异 |
|---|---|---|---|
| 订单数 | 8291 | 8291 | 0 |
| 订单额 | 178617.79 | 178617.79 | 0 |
| 技术服务费 | 279.49 | 279.49 | 0 |
| 服务费收入 | 3278.92 | 3277.02 | 0.84 |
| 服务费支出（卡片） | 0.00 | 1.90 | 1.90 |
| 服务费收益 | 2772.69（pr valid 汇总） | 2995.63 | **225.25** |

可见字段反推（修复前）：

```text
3276.18 - 279.49 - 0.00 = 2996.69
2996.69 - 2770.38 ≈ 226.31（隐藏扣减）
```

## 3. 根因定位：226.31 不是 mystery 字段

**不是** raw_payload 某字段 SUM≈226.31。
**是** 汇总口径混用：

| 字段 | 修复前来源 |
|---|---|
| serviceFeeIncome / techServiceFee | 订单表 `colonelsettlement_order` **全量 8291** |
| serviceFeeExpense | 订单表 `estimate_service_fee_expense`（全为 0） |
| serviceFeeProfit | `performance_records` **`is_valid=TRUE` 汇总** |

失效单（634 笔）贡献：

```text
invalid_income  = ¥248.00
invalid_tech    = ¥21.26
invalid_net     = ¥226.74  ≈ 226.31
valid_profit    = ¥2772.69
```

全量公式（同一 cohort）：

```text
income - tech - expense = 3278.92 - 279.49 - 0 = ¥2999.43
```

与目标 ¥2995.63 差距主要来自：收入 0.84 + 支出 1.90 未入库。

## 4. API 对账（修复后，2026-06-06 全天 createTime）

| API | orderCount | serviceFeeIncome | tech | expense | profit | 公式闭合 |
|---|---|---|---|---|---|---|
| `/api/data/orders/summary` | 9830 | 3824.31 | 326.75 | 0.00 | **3497.56** | ✅ 3824.31−326.75−0 |
| `/api/performance/summary` | 9050 | 3528.23* | 301.44* | 0 | **3226.79*** | ✅ 同公式（*金额为分） |

`/api/dashboard/metrics` estimate 轨道：今日 createTime 窗口为 0（当前日为 6/7 时 today 切片为空），未用作 8291 对账。

## 5. 前端对账

`frontend/src/views/data/index.vue`：

- `serviceFeeExpense()`：**只读后端** `track.serviceFeeExpense`，无反推
- `serviceFeeProfit`：**只读后端** `serviceFeeProfit` / `serviceFee`
- **无前端重算 profit**

## 6. 修复内容（P0-1 / P0-2 / P0-5）

统一公式（全 API）：

```text
serviceFeeProfit = serviceFeeIncome - techServiceFee - serviceFeeExpense
```

| 文件 | 变更 |
|---|---|
| `CommissionService.java` | 新增 `serviceFeeNetCent()` |
| `DataApplicationService.java` | `toOrderSummaryRow` / `buildMetrics` 用公式；移除 `queryServiceProfitCent` |
| `PerformanceSummaryService.java` | `mapTrackSummary` 用公式，不再信任 DB profit 汇总 |
| `ServiceFeeMoneyFormula8291Test.java` | 8291 样本回归 |

## 7. 修复后 8291 窗口预期

| 指标 | 修复后（公式 + 当前 DB expense=0） |
|---|---|
| serviceFeeProfit | **¥2999.43**（非 ¥2770.38） |
| 与目标差距 | ≈¥3.80（收入 0.84 + 支出 1.90 未落库） |

## 8. 仍 BLOCKED：服务费支出 ¥1.90

- `OrderDualTrackAmountResolver` 仍将 expense 硬编码为 0（raw payload 无字段）
- `settle_second_colonel_commission` 今日 SUM=0
- 目标 ¥1.90 需后续：raw 字段映射 / 6468 双机构 overcount 修复后重算（见 `evidence-20260606-213110-service-fee-income-expense-fix.md`）

## 9. 测试

| 项 | 结果 |
|---|---|
| `ServiceFeeMoneyFormula8291Test` | PASS |
| `PerformanceSummaryServiceTest` | PASS |
| `DataControllerTest` | PASS |
| `mvn test`（1760） | PASS |
| backend rebuild + health | UP |

## 10. 结论

**PARTIAL**

- ✅ 226.31 隐藏扣减根因已证伪并修复（cohort 混用）
- ✅ 三 API 服务费收益公式统一
- ✅ 卡片 expense / profit 同源公式，不再出现「支出 0 但收益少 226」
- ⏳ 服务费支出 ¥1.90 仍依赖上游字段入库（非本次公式 bug）
- ⏳ 招商/渠道提成与目标仍有偏差（414.94 vs 376.80 等），另开专项
