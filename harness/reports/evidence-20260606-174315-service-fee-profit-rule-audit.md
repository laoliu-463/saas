# Evidence Report - SERVICE-FEE-PROFIT-RULE-AUDIT

- 时间：2026-06-06 17:43:15 Asia/Shanghai
- 环境：本地工作区，只读代码审查；未部署远端
- 分支 / commit：`696cc902`
- 工作区状态：任务开始前已有多处 dirty / untracked，本轮不回退、不提交
- 修改范围：仅新增本 evidence report；未修改 Java / Vue / SQL / Docker / env

## 用户确认的业务规则

- 预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费
- 结算服务费收益 = 结算服务费收入 - 结算服务费支出

## 当前实现证据

### 1. 业绩计算源头

- `CommissionService.calculateByActivityBuckets()` 当前计算：
  - `serviceFeeNet = serviceFeeIncome - techServiceFee`
  - `grossProfit = serviceFeeNet - bizCommission - channelCommission`
- 当前 `CommissionSummary` 没有 `serviceFeeExpense` 输入字段。

### 2. 业绩记录写入

- `PerformanceCalculationService.buildRecord()` 当前写入：
  - `estimate_service_profit = estimate_service_fee - estimate_tech_service_fee`
  - `effective_service_profit = effective_service_fee - effective_tech_service_fee`
- 写入过程没有扣除独立的 `estimate/effective_service_fee_expense`。

### 3. 汇总层

- `PerformanceSummaryService.aggregateEstimate()` 直接 `SUM(pr.estimate_service_profit)` 作为 `service_fee_profit`。
- `PerformanceSummaryService.aggregateEffective()` 直接 `SUM(pr.effective_service_profit)` 作为 `service_fee_profit`。
- `PerformanceSummaryService.mapTrackSummary()` 再反推：
  - `serviceFeeExpense = serviceFeeIncome - techServiceFee - serviceFeeProfit`
- 因此当前“服务费支出”是展示层反推字段，不是收益计算的前置输入。

### 4. 数据平台 BFF / 订单明细

- `DataApplicationService.buildMetrics()` 使用 `aggregate.serviceProfitCent()` 作为 `metrics.serviceFee`，并用 `income - tech - profit` 反推 `serviceFeeExpense`。
- `DataApplicationService.toOrderDetailVO()` 使用 `perf.getEstimateServiceProfit()` / `perf.getEffectiveServiceProfit()` 展示服务费收益，并用订单收入、技术费、收益反推服务费支出。

### 5. 前端

- 数据平台首页、订单汇总、订单明细只是展示后端字段：
  - `serviceFeeProfit`
  - `estimateServiceProfit`
  - `effectiveServiceProfit`
- 前端没有独立重算服务费收益。

## 验证

命令：

```powershell
mvn -f .\backend\pom.xml "-Dtest=PerformanceCalculationServiceTest,PerformanceSummaryServiceTest,PerformanceMetricsQueryServiceTest" test
```

结果：

- Tests run: 15
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

说明：

- 测试通过证明当前旧公式链路稳定。
- 测试未覆盖用户本次确认的新公式。

## 阶段性结论

当前服务费收益没有按用户本次确认的新规则正确计算。

当前代码实际口径：

- 预估服务费收益 = 预估服务费收入 - 预估技术服务费
- 结算服务费收益 = 结算服务费收入 - 结算技术服务费
- 服务费支出 = 服务费收入 - 技术服务费 - 服务费收益（展示层反推）

用户本次确认口径：

- 预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费
- 结算服务费收益 = 结算服务费收入 - 结算服务费支出

差异：

1. 当前收益计算没有扣独立的“服务费支出”。
2. 当前结算收益仍扣 `effective_tech_service_fee`，而用户新公式没有在结算收益中再次扣技术服务费。
3. 当前“服务费支出”多数路径是由收益倒推出来的，不是计算收益的输入事实。

## Retro Summary

本轮是只读审查 + evidence 记录，无 Harness 流程升级需求。
