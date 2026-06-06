# Evidence Report - SERVICE-FEE-INCOME-RULE-AUDIT

- 时间：2026-06-06 17:36:46 Asia/Shanghai
- 环境：本地工作区，只读代码审查；未部署远端
- 分支 / commit：`696cc902`
- 工作区状态：任务开始前已有多处 dirty / untracked，本轮不回退、不提交
- 修改范围：仅新增本 evidence report；未修改 Java / Vue / SQL / Docker / env

## 用户确认的业务规则

- 预估服务费收入 = 预估订单额 * 服务费率，未扣除技术服务费
- 结算服务费收入 = 结算订单额 * 服务费率 - 技术服务费

## 审查证据

### 1. 订单金额字段来源

- `OrderDualTrackAmountResolver.resolve()` 从抖店 raw payload 解析金额字段：
  - 预估订单额：`pay_goods_amount/order_amount/...`
  - 结算订单额：`settled_goods_amount/settle_amount/...`
  - 预估服务费：`estimated_commission/estimated_service_fee/...`
  - 结算服务费：`commission/settled_commission/service_fee/...`
  - 技术服务费：`estimated_tech_service_fee/tech_service_fee/...`
- 当前实现不是直接用 `订单额 * 服务费率` 本地重算服务费收入，而是依赖上游服务费字段或 fallback 字段。
- `applyInstituteFactToOrder()` 只写 `orderAmount/actualAmount/estimateServiceFee/estimateTechServiceFee`，不写结算轨。
- `applyToOrder()` 写 `settleAmount/effectiveServiceFee/effectiveTechServiceFee`。

### 2. 业绩域收益公式

- `PerformanceCalculationService.buildRecord()` 将订单字段映射到 `performance_records`，并调用 `CommissionService.calculateTrack()` 分别计算预估轨和结算轨。
- `CommissionService.calculateByActivityBuckets()` 当前公式：
  - `serviceFeeNet = serviceFeeIncome - techServiceFee`
  - `grossProfit = serviceFeeNet - bizCommission - channelCommission`
- 这说明“扣技术服务费后的金额”当前语义是 `serviceFeeNet/serviceProfit`，不是 `serviceFeeIncome`。

### 3. 看板 / 数据平台展示

- `PerformanceMetricsQueryService.aggregateRange()`：
  - 预估轨：`service_fee_income_cent = SUM(pr.estimate_service_fee)`
  - 结算轨：`service_fee_income_cent = SUM(pr.effective_service_fee)`
  - `service_profit_cent` 才是 `estimate/effective_service_profit`
- `DataApplicationService.buildMetrics()` 将：
  - `serviceFeeIncome` 设为 `aggregate.serviceFeeIncomeCent()`
  - `serviceFee` 设为 `aggregate.serviceProfitCent()`
- `DataApplicationService.toOrderDetailVO()`：
  - “服务费收入”展示 `estimateServiceFee/effectiveServiceFee`
  - “服务费收益”展示 `estimateServiceProfit/effectiveServiceProfit`
- `frontend/src/views/data/OrderDetailTab.vue` 同样将“服务费收入”绑定到 `estimateServiceFee/effectiveServiceFee`，将“服务费收益”绑定到 `estimateServiceProfit/effectiveServiceProfit`。

### 4. 旧版 Dashboard summary

- `DashboardService.getSummary()` 在无 `performance_records` 时仍回退到 `sum(settle_colonel_commission) as serviceFee`。
- 该旧接口不是双轨服务费收入口径，不能作为这条新业务规则已实现的证据。

## 验证

命令：

```powershell
mvn -f .\backend\pom.xml "-Dtest=OrderDualTrackAmountResolverTest,CommissionServiceTest,PerformanceMetricsQueryServiceTest" test
```

结果：

- Tests run: 15
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

非阻塞噪声：

- `CommissionServiceTest.calculate_shouldFallbackWhenRatioQueryFails` 预期触发 `db offline` fallback 日志，测试仍 PASS。
- JaCoCo 报告出现既有 class execution data mismatch warning，本轮未处理。

## 结论

阶段性结论：当前实现未完全按用户新确认的“结算服务费收入 = 结算订单额 * 服务费率 - 技术服务费”命名口径实现。

- 预估侧：如果上游 `estimate_service_fee` 本身等于 `预估订单额 * 服务费率`，当前展示的“预估服务费收入”可以等价满足；但代码不是本地按费率重算，因此仍依赖上游字段正确性。
- 结算侧：当前“结算服务费收入”展示的是 `effective_service_fee`，未扣 `effective_tech_service_fee`；扣除技术服务费后的值当前展示/聚合在“服务费收益”字段。

当前更准确的代码口径是：

- 服务费收入：`estimate_service_fee / effective_service_fee`
- 服务费收益：`estimate_service_profit / effective_service_profit = 服务费收入 - 技术服务费`

如果业务要求 UI 和 API 中“结算服务费收入”必须直接表示 `结算订单额 * 服务费率 - 技术服务费`，需要做一次字段语义调整或映射调整，不能只靠当前实现证明已符合。

## Retro Summary

本轮是只读审查 + evidence 记录，无 Harness 流程升级需求。
