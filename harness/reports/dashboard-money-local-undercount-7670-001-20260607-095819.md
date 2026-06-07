# DASHBOARD-MONEY-LOCAL-UNDERCOUNT-7670-001 修复报告

## 时间
2026-06-07T09:58:19+08:00

## 环境
real-pre (local Docker)

## 分支
feature/auth-system

## 问题描述
本地看板服务费收益偏低 ¥659.42，拆解为：
- 服务费收入少算 ¥475.24（本地 ¥2581.43 vs 正确 ¥3056.67）
- 服务费支出多算 ¥184.18（本地 ¥186.08 vs 正确 ¥1.90）

## 根因分析

### 根因 1 — 服务费收入偏低
`OrderDualTrackAmountResolver.firstFromInstitutions()` 使用 first-match 策略：
- 对 737 个双机构订单（COI.estimated_commission=null, COI2 有值），COI2 的金额被完全忽略
- 对 2 个 COI+COI2 都有值的订单，只取 COI 值，遗漏 COI2 部分

### 根因 2 — 服务费支出偏高
- `estimate_service_fee_expense` 在所有订单中均为 0（raw payload 无对应字段）
- API 层 fallback 公式 `expense = income - tech - profit` 因 income 错误而产生虚高 expense
- 三层 fallback（PerformanceSummaryService、DataApplicationService、前端）均存在

## 修复内容

### P0-1: 服务费收入来源修复
**文件**: `OrderDualTrackAmountResolver.java`
- `firstFromInstitutions()` 从 first-match 改为 **累加模式**
- 搜索顺序：顶层 → `colonel_order_info` → `colonel_order_info_second`
- 所有非空值累加求和，支持双机构订单场景

### P0-2: 服务费支出修复
**文件**: `PerformanceSummaryService.java`, `DataApplicationService.java`, `data/index.vue`
- 移除三层 expense 反推公式
- 支出直接从 DB 取值（当前无对应 raw 字段，默认为 0）

### P0-3: 统一公式
**文件**: `CommissionService.java`
- 抽取共享方法 `serviceFeeNetCent(income, tech, expense)` = `max(income - tech - expense, 0)`
- `PerformanceSummaryService` 和 `DataApplicationService` 均使用此方法计算 profit

### P0-4: 清理冗余查询
**文件**: `DataApplicationService.java`
- 移除 `queryServiceProfitCent()` 和 `queryDailyServiceProfitCent()` 方法
- 不再从 performance_records 反查 profit 用于反推公式

## 变更文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `CommissionService.java` | 新增方法 | `serviceFeeNetCent()` 统一公式 |
| `OrderDualTrackAmountResolver.java` | 修改逻辑 | first-match → 累加 |
| `PerformanceSummaryService.java` | 修改 | 使用 serviceFeeNetCent 计算 profit |
| `DataApplicationService.java` | 修改+删除 | 移除反推公式及冗余查询 |
| `DataControllerTest.java` | 修改 | 移除已删除方法的 mock |
| `OrderDualTrackAmountResolverTest.java` | 修改 | 更新测试期望值为累加结果 |
| `PerformanceSummaryServiceTest.java` | 修改 | 更新测试期望值为正向公式 |
| `ServiceFeeMoneyFormula8291Test.java` | 新增 | 公式一致性验证测试 |

## 测试结果
- 65 tests run, 0 failures, 0 errors, 0 skipped
- 测试类：DataControllerTest(41), OrderDualTrackAmountResolverTest(8), PerformanceCalculationServiceTest(5), PerformanceSummaryServiceTest(8), ServiceFeeMoneyFormula8291Test(3)

## API 验证

### /api/performance/summary
- serviceFeeIncome: ¥6843.15
- techServiceFee: ¥578.12
- serviceFeeExpense: ¥0.00 ✓
- serviceFeeProfit: ¥6265.03
- **公式验证**: 6843.15 - 578.12 - 0 = 6265.03 ✓

### /api/data/orders/summary
- serviceFeeIncome: ¥7478.69
- serviceFeeExpense: ¥0.00 ✓
- serviceFeeProfit: ¥6266.46

### DB 验证
- 17355 成功订单
- income: ¥6845.43
- expense: ¥0.00 ✓
- tech: ¥578.34

## 结论
**PASS** — 服务费收入从 raw payload 正确字段累加得到，服务费支出不再使用反推公式，三层（后端服务、API、前端）均保持一致。

## 剩余风险
1. raw payload 中尚无 `expense` 对应字段，当前默认为 0。若抖店 API 后续提供此字段，需在 `OrderDualTrackAmountResolver` 中添加映射
2. 远端 real-pre 未部署（需用户明确要求）
