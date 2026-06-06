# DASHBOARD-TODAY-SNAPSHOT-RECON-001 对账报告

## 基本信息

| 字段 | 值 |
|------|-----|
| snapshotAt | 2026-06-06 15:44:16 +08:00 (Asia/Shanghai) |
| 环境 | real-pre (local) |
| 分支 | main |
| 今日窗口 | 2026-06-06 00:00:00 ~ 2026-06-06 15:44:16 |
| 任务类型 | 只读审查 (Scope=recon) |
| 结论 | **FAIL_MONEY_FORMULA_DRIFT** |

---

## 表1：今天正确基准 vs 本地 DB 订单表 vs performance_records vs API

| 指标 | 正确基准 | DB 订单表 (全部) | DB 业绩表 (valid) | /api/orders | /api/data/orders/summary (今天行) | /api/dashboard/metrics (estimate) | /api/performance/summary (estimate) |
|------|-------:|-------:|-------:|-------:|-------:|-------:|-------:|
| 总订单数 | 4716 | **4709** | **4326** | **4709** | **4709** | **4326** | **4359** |
| 订单额 | 99780.33 | **99631.33** | **91503.55** | — | **99631.33** | **91503.55** | **92230.71** |
| 服务费收入 | 1806.52 | **1539.20** | **1422.27** | — | **1539.20** | **1422.27** | **1434.86** |
| 技术服务费 | 155.18 | **154.94** | **143.19** | — | **154.94** | **143.19** | **144.45** |
| 服务费支出 | **1.90** | — | — | — | **415.32** ❌ | — | **386.40** ❌ |
| 服务费收益 | 1649.44 | — | **1279.08** | — | **1384.26** | **1279.08** | **1290.41** |
| 招商提成 | 164.94 | — | **191.53** | — | — | **191.53** | **193.20** |
| 渠道/媒介提成 | 213.18 | — | **191.53** | — | — | **191.53** | **193.20** |
| 毛利 | 1271.30 | — | **896.02** | — | **968.94** | **896.02** | **904.01** |
| 结算轨各指标 | 全 0 | 全 0 | 全 0 | — | — | 全 0 | 全 0 |

### 数值说明

- **DB 订单表 (全部)**: `pay_time` 今天窗口，`deleted=0`，包含 status=1 和 status=4
- **DB 业绩表 (valid)**: JOIN `performance_records` + 订单表，`is_valid=TRUE`
- **/api/data/orders/summary**: 包含全部订单（含 reversed），`serviceFeeExpense` 直接从 `CommissionService.bizCommission + channelCommission` 取值
- **/api/dashboard/metrics**: 仅 valid 订单，`serviceFeeProfit` 直接从 DB `estimate_service_profit` 读取，无 `serviceFeeExpense` 字段
- **/api/performance/summary**: cohort 时间列 = `COALESCE(order_create_time, create_time)`，非 pay_time，因此订单数/金额与 pay_time 口径不同

---

## 表2：差异归因

| 指标 | 正确值 | 本地值 (最接近源) | 差异 | 归因 |
|------|-------:|-------:|-------:|------|
| 总订单数 | 4716 | 4709 (DB) | **-7** | **订单同步时间窗口微偏** — 本地快照比正确基准早或上游持续入库 |
| 订单额 | 99780.33 | 99631.33 (DB) | **-149.00** | **同上** — 7 单差异导致金额偏低 |
| 服务费收入 | 1806.52 | 1539.20 (DB全量) | **-267.32** ❌ | **订单级服务费字段偏低** — DB `estimate_service_fee` 总和与正确基准差异显著 |
| 技术服务费 | 155.18 | 154.94 (DB) | **-0.24** | **近似一致** — 微小舍入误差 |
| 服务费支出 | 1.90 | 415.32 (API summary) | **+413.42** ❌ | **公式口径错误** — `serviceFeeExpense = bizCommission + channelCommission`（应为平台费 ≈ 1.90） |
| 服务费收益 | 1649.44 | 1384.26 (API summary) | **-265.18** ❌ | **上游服务费收入偏低 + 公式无 serviceFeeExpense 扣除** |
| 招商提成 | 164.94 | 191.53 (DB valid) | **+26.59** | **提成基数偏大** — 代码提成基数 = 收入 - 技术费，未扣服务费支出 |
| 渠道提成 | 213.18 | 191.53 (DB valid) | **-21.65** | **上游差异** — 正确基准渠道提成高于本地 |
| 毛利 | 1271.30 | 968.94 (API summary) | **-302.36** ❌ | **级联误差** — 服务费收入偏低 + 公式错误 + 提成基数偏大 |
| 结算轨全 0 | 全 0 | 全 0 | 0 | **BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE** — 无结算样本，不算本地错误 |

---

## 表3：公式核对

| 指标 | 正确公式 | 本地公式 | 是否一致 | 证据 |
|------|---------|---------|---------|------|
| 服务费支出 | 平台实际服务费（≈ 1.90） | `bizCommission + channelCommission` | **❌ 不一致** | `PerformanceSummaryService.java:308` / `DataApplicationService.java:2020` / `index.vue:479-483` |
| 提成基数 (serviceFeeNet) | 未明确（可能是 收入 - 技术费 - 支出） | `serviceFeeIncome - techServiceFee` | **⚠️ 可能偏差** | `CommissionService.java:250` |
| 服务费收益 | 收入 - 技术费 - 支出 | DB 直接存 `estimate_service_profit` | **⚠️ 依赖上游写入** | `performance_records.estimate_service_profit` 不含支出扣除 |
| 毛利 | 服务费收益 - 招商 - 渠道 | `serviceFeeNet - bizCommission - channelCommission` | **⚠️ 级联偏差** | `CommissionService.java:271` — 因 serviceFeeNet 未扣支出，毛利偏高；但因收入偏低，实际毛利反而偏低 |
| 前端服务费支出 fallback | 不应有 fallback | `commission 或 bizCommission + channelCommission` | **❌ 不一致** | `index.vue:479-483` — dashboard metrics 无 serviceFeeExpense 字段时 fallback 到提成合计 |
| 前端服务费收益 tooltip | 收入 - 技术费 - 支出 | "收入 - 技术费" | **❌ 不一致** | `OrderList.vue:521` |
| 前端服务费支出 tooltip | 平台费 | "招商提成 + 渠道提成" | **❌ 不一致** | `OrderList.vue:512` |

---

## BUG 清单

### BUG-1：后端 serviceFeeExpense 公式错误（2 处）

**位置 1**: `PerformanceSummaryService.java:308`
```java
track.setServiceFeeExpense(recruiter + channel);
```

**位置 2**: `DataApplicationService.java:2020`
```java
vo.setServiceFeeExpense(centToYuan(summary.bizCommission() + summary.channelCommission()));
```

**影响**: `/api/data/orders/summary`、`/api/performance/summary` 返回错误的 serviceFeeExpense

**修复**: serviceFeeExpense 应从独立字段或公式计算：`serviceFeeIncome - techServiceFee - serviceFeeProfit`，或从 performance_records 中新增字段读取

### BUG-2：前端 dashboard serviceFeeExpense fallback 错误

**位置**: `frontend/src/views/data/index.vue:479-483`
```typescript
const serviceFeeExpense = (track: Record<string, any>) => {
  const explicit = toNumber(track?.serviceFeeExpense)
  if (explicit > 0) return formatMoney(explicit)
  return formatMoney(toNumber(track?.commission) || toNumber(track?.bizCommission) + toNumber(track?.channelCommission))
}
```

**影响**: `/api/dashboard/metrics` 不返回 serviceFeeExpense，前端 fallback 到 commission 合计

**修复**: 后端 dashboard metrics 应返回正确的 serviceFeeExpense；前端 fallback 应移除或改为 `serviceFeeIncome - techServiceFee - serviceFeeProfit`

### BUG-3：前端 tooltip 公式描述错误（2 处）

**位置 1**: `OrderList.vue:512` — 服务费支出 tooltip 写 "招商提成 + 渠道提成"
**位置 2**: `OrderList.vue:521` — 服务费收益 tooltip 写 "收入 - 技术费"（缺少 "- 支出"）

### BUG-4：CommissionService 提成基数未扣服务费支出

**位置**: `CommissionService.java:250`
```java
long serviceFeeNet = Math.max(serviceFeeIncome - techServiceFee, 0L);
```

**正确公式可能为**: `serviceFeeIncome - techServiceFee - serviceFeeExpense`

**影响**: 提成基数偏大 → 招商提成和渠道提成偏高 → 毛利偏差

### BUG-5：performance_records.estimate_service_profit 未扣除服务费支出

**影响**: 所有从 performance_records 读取 serviceFeeProfit 的接口均不含支出扣除，导致级联偏差

---

## 判断标准回答

### 1. 本地今天订单数是否接近 4716？

**是**。本地 4709 单，差 7 单（0.15%）。订单覆盖基本正确，差异属于快照时间微偏或上游持续入库。

### 2. 本地今天订单额是否接近 99780.33？

**基本接近**。本地 99631.33，差 149.00（0.15%）。与订单数差异比例一致，由 7 单缺失导致。

### 3. 本地今天服务费收入是否接近 1806.52？

**❌ 不接近**。本地 1539.20，差 **267.32**（14.8%）。这是最显著的数据差异。原因可能是：
- 上游同步时订单级 `estimate_service_fee` 字段取值口径不同
- 7 单缺失不足以解释 267 元差异（需平均每单 38 元服务费，远超均值 0.33 元）
- **可能是上游活动/商品维度的服务费费率配置差异**

### 4. 本地今天技术服务费是否接近 155.18？

**是**。本地 154.94，差 0.24（0.15%）。

### 5. 本地 serviceFeeExpense 是否仍错？

**❌ 仍然错**。所有 3 个 API 端点的 serviceFeeExpense 都等于 `bizCommission + channelCommission`：
- `/api/data/orders/summary` → 415.32（正确应为 1.90）
- `/api/performance/summary` → 386.40（正确应为 1.90）
- 前端 dashboard fallback → commission 合计（正确应为 1.90）

### 6. 本地服务费收益/毛利是否按正确公式？

**⚠️ 内部一致但基准偏差**：
- 本地公式内部自洽：`serviceFeeProfit = income - techFee - expense`，`grossProfit = serviceFeeProfit - biz - channel`
- 但因为 serviceFeeExpense 定义错误 + 上游服务费收入偏低，最终数值与基准不符

### 7. 下一步是否进入 DASH-RECON-MONEY-DRIFT-001？

**是**。建议进入 DASH-RECON-MONEY-DRIFT-001，重点修复：

1. **P0**: 修正 serviceFeeExpense 公式（3 处代码 + 2 处 tooltip）
2. **P0**: 确认 CommissionService 提成基数是否应扣除 serviceFeeExpense
3. **P1**: 排查服务费收入偏差根因（上游活动/费率配置 vs 本地取值逻辑）
4. **P2**: 后端 dashboard metrics 增加 serviceFeeExpense 字段，移除前端 fallback

---

## 结算轨结论

**BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE**

所有 API 的结算轨（settle/effective）指标全 0，与正确基准一致。无结算样本，不算本地结算逻辑错误。

---

## 总体结论

**FAIL_MONEY_FORMULA_DRIFT**

| 维度 | 结论 |
|------|------|
| 订单覆盖 | **PASS** — 4709 ≈ 4716（差 7 单，0.15%） |
| 订单额 | **PASS** — 99631.33 ≈ 99780.33（差 0.15%） |
| 服务费收入 | **FAIL** — 1539.20 vs 1806.52（差 14.8%） |
| 技术服务费 | **PASS** — 154.94 ≈ 155.18 |
| 服务费支出公式 | **FAIL** — `bizCommission + channelCommission`（正确应为平台费 ≈ 1.90） |
| 服务费收益 | **FAIL** — 级联偏差（上游收入偏低 + 公式偏差） |
| 毛利 | **FAIL** — 级联偏差 |
| 结算轨 | **BLOCKED** — 无结算样本 |
| 前端 tooltip | **FAIL** — 公式描述错误 |
| 前端 fallback | **FAIL** — dashboard 服务费支出 fallback 到提成合计 |
