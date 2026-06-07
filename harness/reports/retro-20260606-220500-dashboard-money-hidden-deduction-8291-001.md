# Retro — DASHBOARD-MONEY-HIDDEN-DEDUCTION-8291-001

## 发现

1. 「226 隐藏扣减」= **634 笔 is_valid=false 订单**的收入/技术费计入订单汇总，但收益只汇总 valid 业绩。
2. 不是 recruiter+channel 被误当 expense（旧 bug 已修）。
3. 8291 窗口应用统一公式后 profit≈2999，距目标 2995 仅差 income 0.84 + expense 1.90。

## 改进

- 所有 summary API 强制 `CommissionService.serviceFeeNetCent()`，禁止混用 DB profit 汇总。
- 8291 fixture 单测防回归。

## 后续

1. `OrderDualTrackAmountResolver` 解析真实 expense 字段（目标 ¥1.90）。
2. 6468 双机构 overcount 1.90 历史行重算（已有 evidence 报告）。
3. performance/summary 与 orders/summary 订单数 cohort（valid vs all）是否产品层需统一 — 待 ADR。

## Harness

建议 preflight 增加：`serviceFeeProfit == income - tech - expense` 断言（±0.01 元）。
