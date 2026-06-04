# DASHBOARD-MONEY-AUDIT-001 数据看板资金口径审查报告

> **任务**：DASHBOARD-MONEY-AUDIT-001 — 数据看板 / 资金指标只读审查
> **时间**：2026-06-04 13:19 +08:00
> **类型**：diagnosis / audit（只读审查，不修改业务代码）
> **分支**：feature/auth-system
> **HEAD**：d32bbef5

---

## 1. 结论

- **Dashboard 资金口径：FAIL**
- 双轨字段在实体层完整，但计算层和展示层存在 P0 级口径错误
- 最大风险点：`performance_records.settle_amount` 被回退逻辑污染（全部 404 条有效记录 settle_amount = pay_amount），导致结算轨所有金额指标虚高
- 旧版 `/dashboard/summary` 为单轨接口，不支持双轨展示
- V1 不做的毛利指标仍在前端多处展示

### 关键发现汇总

| 级别 | 数量 | 概要 |
| --- | --- | --- |
| P0 | 4 | settle_amount 回退污染、旧版接口单轨、聚合被污染、V1 毛利展示 |
| P1 | 4 | 回退字段错误、talentCommission 计算错误、estimate 轨时间字段错误、缺 time_filter_type |
| P2 | 2 | 单位换算一致性待确认、前端 null→0 格式化 |

---

## 2. 需求口径摘录

### 2.1 双轨模型

V1 要求订单域存储双轨金额事实：

| 轨道 | 订单额 | 服务费收入 | 技术服务费 |
| --- | --- | --- | --- |
| 预估/成交（estimate） | pay_amount | estimate_service_fee | estimate_tech_service_fee |
| 结算（effective） | settle_amount | effective_service_fee | effective_tech_service_fee |

### 2.2 业绩域计算公式

```
service_profit = service_fee - tech_service_fee
recruiter_commission = service_profit × recruiter_rate
channel_commission = service_profit × channel_rate
gross_profit = service_profit - recruiter_commission - channel_commission（V1 不展示）
```

### 2.3 V1 不做清单

- V1 不做毛利口径扩展（`docs/02-V1不做清单.md`、`CURRENT_STATE.md`）
- 毛利字段如存在只能作为遗留/预留，不应影响验收口径

### 2.4 时间口径

- 预估轨按 pay_time / create_time 决定订单进入统计
- 结算轨按 settle_time 决定订单进入统计
- 不允许混用

---

## 3. 当前实现链路

```
抖音订单接口 (6468/2704)
  → OrderSyncService / OrderDualTrackAmountResolver
    → colonelsettlement_order（订单事实表，双轨金额字段完整）
      → PerformanceCalculationService.calculatePerformance()
        → CommissionService.calculateTrack()（分别 estimate/effective）
          → performance_records（业绩记录表，双轨字段完整）
            → PerformanceMetricsQueryService.aggregateRange()（按 timeField 选双轨列）
              → DataApplicationService.buildMetrics()（/dashboard/metrics 双轨 API）
                → DualTrackMetricsVO（settle + estimate 两轨 MetricsVO）
                  → 前端 data/index.vue（resolveDualTrackMetrics 解包）

DashboardService.getSummary()（/dashboard/summary 旧版单轨 API）
  → DashboardService.Summary（扁平 DTO，无双轨结构）
    → 前端 dashboard/index.vue（4 张卡片，单轨展示）
```

---

## 4. 字段映射表

### 4.1 订单域（colonelsettlement_order）

| 需求字段 | 数据库字段 | Entity 字段 | 类型 | 单位 | 是否一致 | 风险 |
| --- | --- | --- | --- | --- | --- | --- |
| 订单金额 | order_amount | orderAmount | Long | 分 | ✅ | — |
| 实付金额 | actual_amount | actualAmount | Long | 分 | ✅ | — |
| 结算金额 | settle_amount | settleAmount | Long | 分 | ✅ | — |
| 预估服务费 | estimate_service_fee | estimateServiceFee | Long | 分 | ✅ | — |
| 结算服务费 | effective_service_fee | effectiveServiceFee | Long | 分 | ✅ | — |
| 预估技术服务费 | estimate_tech_service_fee | estimateTechServiceFee | Long | 分 | ✅ | — |
| 结算技术服务费 | effective_tech_service_fee | effectiveTechServiceFee | Long | 分 | ✅ | — |
| 付款时间 | pay_time | payTime | LocalDateTime | — | ✅ | — |
| 创建时间 | order_create_time | orderCreateTime | LocalDateTime | — | ✅ | — |
| 结算时间 | settle_time | settleTime | LocalDateTime | — | ✅ | — |
| 团长佣金 | settle_colonel_commission | settleColonelCommission | Long | 分 | ⚠️ | P1-001 旧版误用 |

### 4.2 业绩域（performance_records）

| 需求字段 | 数据库字段 | Entity 字段 | 是否一致 | 风险 |
| --- | --- | --- | --- | --- |
| 付款订单额 | pay_amount | payAmount | ✅ | — |
| 结算订单额 | settle_amount | settleAmount | ❌ | P0-001 被回退污染 |
| 预估服务费收入 | estimate_service_fee | estimateServiceFee | ✅ | — |
| 结算服务费收入 | effective_service_fee | effectiveServiceFee | ✅ | — |
| 预估技术服务费 | estimate_tech_service_fee | estimateTechServiceFee | ✅ | — |
| 结算技术服务费 | effective_tech_service_fee | effectiveTechServiceFee | ✅ | — |
| 预估服务费收益 | estimate_service_profit | estimateServiceProfit | ✅ | — |
| 结算服务费收益 | effective_service_profit | effectiveServiceProfit | ✅ | — |
| 预估招商提成 | estimate_recruiter_commission | estimateRecruiterCommission | ✅ | — |
| 结算招商提成 | effective_recruiter_commission | effectiveRecruiterCommission | ✅ | — |
| 预估渠道提成 | estimate_channel_commission | estimateChannelCommission | ✅ | — |
| 结算渠道提成 | effective_channel_commission | effectiveChannelCommission | ✅ | — |
| 预估毛利 | estimate_gross_profit | estimateGrossProfit | ⚠️ | P0-004 V1 不展示 |
| 结算毛利 | effective_gross_profit | effectiveGrossProfit | ⚠️ | P0-004 V1 不展示 |

### 4.3 API DTO → 前端绑定

| API JSON 字段 | VO 字段 | 前端绑定 | 是否一致 | 风险 |
| --- | --- | --- | --- | --- |
| settle.orderAmount | MetricsVO.orderAmount | metrics.settle.orderAmount | ✅ | 值被 P0-001 污染 |
| settle.serviceFeeIncome | MetricsVO.serviceFeeIncome | metrics.settle.serviceFeeIncome | ✅ | — |
| settle.serviceProfit | MetricsVO.serviceProfit | metrics.settle.serviceProfit | ✅ | — |
| estimate.orderAmount | MetricsVO.orderAmount | metrics.estimate.orderAmount | ✅ | — |
| *.grossProfit | MetricsVO.grossProfit | metrics.*.grossProfit | ⚠️ | P0-004 V1 不做 |

---

## 5. 双轨计算审查

### 5.1 预估轨（estimate）

| 计算步骤 | 代码路径 | 公式 | 结果 |
| --- | --- | --- | --- |
| 服务费收益 | CommissionService.calculateTrack() | estimate_service_fee - estimate_tech_service_fee | ✅ PASS |
| 招商提成 | CommissionService.calculateTrack() | serviceFeeNet × recruiterRate | ✅ PASS |
| 渠道提成 | CommissionService.calculateTrack() | serviceFeeNet × channelRate | ✅ PASS |
| 提成基数保护 | CommissionService.calculateTrack() | Math.max(serviceFeeIncome - techServiceFee, 0) | ✅ PASS |

**结论**：预估轨计算逻辑正确。

### 5.2 结算轨（effective）

| 计算步骤 | 代码路径 | 公式 | 结果 |
| --- | --- | --- | --- |
| settle_amount 来源 | PerformanceCalculationService:113 | settleAmount > 0 ? settleAmount : actualAmount | ❌ P0-001 |
| 服务费收益 | CommissionService.calculateTrack() | effective_service_fee - effective_tech_service_fee | ✅ 公式正确 |
| 招商提成 | CommissionService.calculateTrack() | serviceFeeNet × recruiterRate | ✅ 公式正确 |
| 渠道提成 | CommissionService.calculateTrack() | serviceFeeNet × channelRate | ✅ 公式正确 |

**结论**：结算轨计算公式正确，但 `settle_amount` 被回退逻辑污染，导致结算轨订单额虚高。

### 5.3 双轨互污染检查

| 检查项 | 结果 |
| --- | --- |
| estimate 字段是否被 effective 覆盖 | ✅ 未发现 |
| effective 字段是否被 estimate 覆盖 | ❌ P0-001：settle_amount 回退到 actual_amount |
| 只算一条轨道 | ❌ P0-002：旧版 `/dashboard/summary` 只有单轨 |
| 前端只显示一条轨道 | ✅ 新版 data/index.vue 显示双轨 |
| 结算轨为 0 时被错误显示 | ⚠️ effective_service_fee=0 时前端显示 ¥0.00 |

---

## 6. Dashboard 指标审查

### 6.1 新版双轨看板（/dashboard/metrics → data/index.vue）

| 指标 | 预估轨 | 结算轨 | 结果 |
| --- | --- | --- | --- |
| 订单数 | ✅ 按 create_time 统计 | ⚠️ 按 settle_time 统计（全为 NULL） | RISK |
| 订单总额 | ✅ 用 pay_amount | ❌ 用 settle_amount（被 P0-001 污染） | FAIL |
| 服务费收入 | ✅ estimate_service_fee | ✅ effective_service_fee | PASS |
| 技术服务费 | ✅ estimate_tech_service_fee | ✅ effective_tech_service_fee | PASS |
| 服务费收益 | ✅ estimate_service_profit | ✅ effective_service_profit | PASS |
| 招商提成 | ✅ estimate_recruiter_commission | ✅ effective_recruiter_commission | PASS |
| 渠道提成 | ✅ estimate_channel_commission | ✅ effective_channel_commission | PASS |
| 毛利 | ⚠️ 展示但 V1 不做 | ⚠️ 展示但 V1 不做 | RISK (P0-004) |

### 6.2 旧版单轨看板（/dashboard/summary → dashboard/index.vue）

| 指标 | 字段 | 结果 |
| --- | --- | --- |
| 订单数 | count(*) | PASS |
| 订单总额 | sum(order_amount) | ⚠️ 用 order_amount 非 pay_amount (P1-001) |
| 服务费收入 | sum(settle_colonel_commission) | ❌ 用团长佣金冒充服务费 (P1-001) |
| 双轨支持 | 无 | ❌ 单轨接口 (P0-002) |

---

## 7. 时间口径审查

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 预估轨时间字段 | 使用 create_time | PerformanceMetricsQueryService.aggregateRange() |
| 结算轨时间字段 | 使用 settle_time | PerformanceMetricsQueryService.aggregateRange() |
| estimate 轨需求时间 | 应为 pay_time | 需求文档要求按付款时间 |
| 差异 | P1-003：create_time ≠ pay_time | 订单创建 ≠ 付款 |
| 旧版 Dashboard 时间过滤 | 始终用 settle_time | DashboardService.applyRange() |
| time_filter_type 支持 | 旧版不支持 | DashboardController 无此参数 |
| 新版 time_filter_type | 支持 create_time / settle_time | DataApplicationService.resolveOrderTrackColumns() |
| 预估轨按 settle_time 过滤 | ❌ 不会发生 | 新版正确按 create_time |
| 结算轨按 pay_time 过滤 | ❌ 不会发生 | 新版正确按 settle_time |

---

## 8. SQL 与 API 对账

### 8.1 订单基础金额汇总（colonelsettlement_order）

```sql
SELECT count(*) AS cnt,
       sum(order_amount) AS order_amount,
       sum(actual_amount) AS actual_amount,
       sum(settle_amount) AS settle_amount,
       sum(estimate_service_fee) AS est_svc_fee,
       sum(effective_service_fee) AS eff_svc_fee,
       sum(estimate_tech_service_fee) AS est_tech_fee,
       sum(effective_tech_service_fee) AS eff_tech_fee
FROM colonelsettlement_order;
```

| 指标 | SQL 结果 |
| --- | --- |
| count | 460 |
| order_amount | 893,832 分 (¥8,938.32) |
| settle_amount | 0 分 |
| estimate_service_fee | 14,438 分 (¥144.38) |
| effective_service_fee | 0 分 |
| estimate_tech_service_fee | 1,472 分 (¥14.72) |
| effective_tech_service_fee | 0 分 |

**关键事实**：所有 460 个订单 `settle_time IS NULL`，无订单已结算。

### 8.2 业绩表汇总（performance_records）

```sql
SELECT count(*) AS cnt,
       sum(pay_amount) AS pay_amt,
       sum(settle_amount) AS settle_amt,
       sum(estimate_service_fee) AS est_svc,
       sum(effective_service_fee) AS eff_svc,
       sum(estimate_service_profit) AS est_profit,
       sum(estimate_recruiter_commission) AS est_recruit,
       sum(estimate_channel_commission) AS est_channel
FROM performance_records
WHERE status = 1;
```

| 指标 | SQL 结果 | 预期 | 差异 |
| --- | --- | --- | --- |
| count | 404 | 404 | — |
| pay_amount | 771,125 分 | 771,125 | ✅ |
| **settle_amount** | **771,125 分** | **0** | ❌ **应=0，实际=pay_amount** |
| estimate_service_fee | 12,125 分 | 12,125 | ✅ |
| effective_service_fee | 0 分 | 0 | ✅ |
| estimate_service_profit | 10,887 分 | 10,887 | ✅ |
| estimate_recruiter_commission | 1,643 分 | 1,643 | ✅ |
| estimate_channel_commission | 1,643 分 | 1,643 | ✅ |

**P0-001 确认**：`performance_records.settle_amount = 771,125` 但 `colonelsettlement_order.settle_amount = 0`。回退逻辑 `settleAmount > 0 ? settleAmount : actualAmount` 导致所有记录的 settle_amount = actual_amount = pay_amount。

### 8.3 差异总表

| 指标 | 订单表 SQL | 业绩表 SQL | 差异 | 原因 |
| --- | --- | --- | --- | --- |
| settle_amount | 0 | 771,125 | 771,125 分 | P0-001 回退逻辑 |
| effective_service_fee | 0 | 0 | 0 | ✅ 一致 |
| effective_service_profit | — | 0 | 0 | ✅ |

---

## 9. 前端展示审查

### 9.1 新版数据看板（data/index.vue，1151 行）

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 双轨解包 | ✅ resolveDualTrackMetrics 正确 | dashboard-metrics.ts |
| 金额单位 | ✅ 后端返回元，前端不再除100 | centToYuan 在后端 |
| label 与字段匹配 | ✅ 服务费收入=serviceFeeIncome | 第4张卡片 |
| 技术服务费 | ✅ techServiceFee | 收入分拆标签组 |
| 招商提成 | ✅ recruiterCommission | 收入分拆标签组 |
| 渠道提成 | ✅ channelCommission | 收入分拆标签组 |
| **毛利展示** | ❌ 第4张卡片显示 grossProfit | P0-004 V1 不做 |
| 业绩双轨汇总 | ❌ 显示"预估轨毛利"和"结算轨毛利" | P0-004 V1 不做 |
| null→0 格式化 | ⚠️ 可能误导 | P2 |

### 9.2 旧版仪表盘（dashboard/index.vue，455 行）

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 金额单位 | ✅ 除以100转元 | 前端 /100 |
| 双轨 | ❌ 只有单轨 | P0-002 |
| 服务费字段 | ❌ 绑定错误 | P1-001 |

---

## 10. 测试覆盖审查

| 测试项 | 状态 | 说明 |
| --- | --- | --- |
| 订单双轨字段映射 | ❌ 缺失 | 无专项测试 |
| 业绩双轨计算 | ⚠️ 部分 | CommissionServiceTest 存在但未覆盖双轨回退 |
| Dashboard summary SQL | ❌ 缺失 | DashboardServiceTest 未验证金额口径 |
| 前端卡片字段映射 | ❌ 缺失 | 无 dashboard 前端测试 |
| 待结算订单不进 effective | ❌ 缺失 | 核心测试缺口 |
| 技术服务费扣减 | ⚠️ 部分 | CommissionServiceTest 有基础覆盖 |
| 提成基数=服务费收益 | ⚠️ 部分 | CommissionServiceTest 有基础覆盖 |
| V1 不展示毛利 | ❌ 缺失 | 无测试验证毛利是否展示 |

**测试缺口总结**：8 项中 4 项缺失、3 项部分覆盖、1 项完整。核心缺口是 settle_amount 回退逻辑和双轨隔离测试。

---

## 11. 问题清单

### P0 问题

#### DASH-MONEY-P0-001：settle_amount 回退逻辑污染业绩表

- **等级**：P0
- **现象**：performance_records.settle_amount = pay_amount（全部 404 条），尽管实际无订单已结算
- **证据**：`PerformanceCalculationService.java:113` — `long settleAmount = nvl(order.getSettleAmount()) > 0 ? nvl(order.getSettleAmount()) : nvl(order.getActualAmount());`
- **SQL 证据**：订单表 settle_amount=0，业绩表 settle_amount=771,125
- **影响**：结算轨订单总额虚高 771,125 分（¥7,711.25），结算轨所有衍生指标不可信
- **根因**：回退到 actualAmount 使未结算订单也有"结算金额"
- **建议修复**：删除回退逻辑，`settleAmount` 直接取 `order.getSettleAmount()`（为 0 则为 0）
- **涉及文件**：`PerformanceCalculationService.java`
- **建议测试**：待结算订单 settle_amount 必须=0

#### DASH-MONEY-P0-002：旧版 /dashboard/summary 是单轨接口

- **等级**：P0
- **现象**：DashboardController.getSummary() 返回扁平 Summary DTO，无双轨结构
- **证据**：`DashboardController.java:32`、`DashboardService.Summary` 内部类
- **影响**：旧版看板（dashboard/index.vue）无法展示双轨数据
- **根因**：旧版接口设计早于双轨需求
- **建议修复**：废弃旧版接口，统一使用 /dashboard/metrics
- **涉及文件**：`DashboardController.java`、`DashboardService.java`、`dashboard/index.vue`
- **建议测试**：确认旧版看板不再被 V1 验收引用

#### DASH-MONEY-P0-003：aggregateDashboardSummary() 被 P0-001 污染

- **等级**：P0
- **现象**：PerformanceMetricsQueryService.aggregateDashboardSummary() 从 performance_records 读 settle_amount，值被 P0-001 回退污染
- **证据**：`PerformanceMetricsQueryService.java` — aggregateDashboardSummary() 始终用 settle_amount
- **影响**：任何调用 aggregateDashboardSummary 的 API 返回的 orderAmount 虚高
- **根因**：P0-001 的下游效应
- **建议修复**：先修 P0-001，此问题自动解决
- **涉及文件**：`PerformanceMetricsQueryService.java`
- **建议测试**：聚合查询 settle_amount 必须=0（当前无已结算订单）

#### DASH-MONEY-P0-004：V1 不做毛利但前端仍展示

- **等级**：P0
- **现象**：data/index.vue 第4张卡片显示 grossProfit；业绩双轨汇总显示"预估轨毛利"和"结算轨毛利"
- **证据**：`MetricsVO.java` 含 grossProfit 字段；`data/index.vue` 多处引用
- **影响**：V1 验收口径风险——展示了 V1 明确不做的指标
- **根因**：代码预留了毛利字段，前端未做 V1 裁剪
- **建议修复**：前端隐藏毛利展示，或加 V1 不纳入验收标记
- **涉及文件**：`MetricsVO.java`、`data/index.vue`
- **建议测试**：验证毛利字段不在 V1 验收页面展示

### P1 问题

#### DASH-MONEY-P1-001：旧版 DashboardService 回退字段错误

- **等级**：P1
- **现象**：DashboardService 回退模式用 `order_amount` 和 `settle_colonel_commission`（团长佣金），非需求中的服务费收入字段
- **证据**：`DashboardService.java` — `.select("sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")`
- **影响**：旧版看板服务费收入数据错误（显示的是团长佣金）
- **建议修复**：如保留旧版接口，改为 estimate_service_fee；否则废弃旧版
- **涉及文件**：`DashboardService.java`

#### DASH-MONEY-P1-002：DataApplicationService.buildMetrics talentCommission 计算逻辑错误

- **等级**：P1
- **现象**：`talentCommission = serviceFeeIncome - techServiceFee - serviceProfit`，但 serviceProfit 已经是 serviceFee - techServiceFee 的结果，导致 talentCommission = 0
- **证据**：`DataApplicationService.java` — buildMetrics() 方法
- **影响**：talentCommission 始终为 0 或接近 0
- **建议修复**：talentCommission = recruiterCommission + channelCommission
- **涉及文件**：`DataApplicationService.java`

#### DASH-MONEY-P1-003：estimate 轨使用 create_time 而非 pay_time

- **等级**：P1
- **现象**：预估轨按 create_time 过滤订单，但需求要求按付款时间（pay_time）
- **证据**：`PerformanceMetricsQueryService.java` — isEstimateTrack() 基于 create_time 判定
- **影响**：预估轨统计的订单范围可能与需求不一致
- **建议修复**：确认需求后改为 pay_time，或明确 create_time 为 V1 口径
- **涉及文件**：`PerformanceMetricsQueryService.java`

#### DASH-MONEY-P1-004：旧版 DashboardController 无 time_filter_type 参数

- **等级**：P1
- **现象**：旧版接口始终用 settle_time 过滤，不支持切换时间口径
- **证据**：`DashboardController.java` — getSummary() 参数无 timeFilterType
- **影响**：旧版看板时间过滤固定为结算时间，预估轨按结算时间过滤会导致未结算单消失
- **建议修复**：如保留旧版接口，增加 time_filter_type；否则废弃
- **涉及文件**：`DashboardController.java`

### P2 问题

#### DASH-MONEY-P2-001：前端 null→¥0.00 格式化可能误导

- **等级**：P2
- **现象**：金额字段为 null 时前端格式化为 ¥0.00，与真实的 0 值无法区分
- **影响**：用户可能误解"无数据"为"金额为零"

#### DASH-MONEY-P2-002：单位换算路径需确认一致性

- **等级**：P2
- **现象**：新版 API 在后端 centToYuan 转元，旧版 API 返回分由前端 /100
- **影响**：两条路径共存增加维护风险

---

## 12. 是否建议进入修复

### 建议拆分为以下后续任务：

#### DASHBOARD-MONEY-FIX-001（P0 修复）

1. 修复 PerformanceCalculationService:113 回退逻辑
2. 清理 performance_records 历史数据（settle_amount 重置为 0）
3. 前端隐藏毛利展示（V1 裁剪）
4. 修复 DataApplicationService.buildMetrics talentCommission 计算

#### DASHBOARD-MONEY-FIX-002（旧版接口治理）

1. 评估旧版 /dashboard/summary 是否仍有 V1 验收引用
2. 如需保留：修复字段映射（P1-001）+ 增加双轨支持 + time_filter_type
3. 如不需保留：废弃旧版接口 + 前端切换到新版

#### DASHBOARD-MONEY-TEST-001（测试补齐）

1. 待结算订单 settle_amount=0 测试
2. 双轨隔离测试（estimate 不影响 effective）
3. Dashboard 聚合与 SQL 对账测试
4. 前端毛利不展示测试

#### DASHBOARD-MONEY-VERIFY-001（运行态验证）

1. 修复后 real-pre 重启验证
2. SQL 与 API 重新对账
3. 前端双轨展示验收
