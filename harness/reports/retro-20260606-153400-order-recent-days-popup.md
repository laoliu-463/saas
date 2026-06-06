# Retro: 订单明细页「近N天」弹窗筛选

## 变更摘要

- `OrderList.vue`：「近N天」改为点击弹出选项（今天 / 昨天 / 15天 / 30天），选中后自动刷新汇总与明细查询。
- `order-list-time-presets.ts`：抽取日期范围计算，便于单测与复用。

## 验证

- 单测：`order-list-time-presets.test.ts` 5/5 PASS；`OrderList.test.ts` 新增弹窗用例 PASS。
- 构建：frontend build PASS；harness full scope PASS（`evidence-20260606-153311.md`）。

## Harness 观察

- 无需升级 harness 流程；现有 agent-do 已覆盖构建、重启、健康检查、preflight。

## 剩余风险

- 弹窗选项固定为 4 项，未含 7 天 / 90 天；若产品需扩展，可在 `recentDaysOptions` 追加。
- 明细 Tab 通过 props 继承 `dateRange`，无需额外改动。
