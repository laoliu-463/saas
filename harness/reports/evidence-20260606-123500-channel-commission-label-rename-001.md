# Evidence: 渠道提成文案收口（媒介 → 渠道）

- 任务 ID: DASH-CHANNEL-COMMISSION-LABEL-001
- 时间: 2026-06-06
- 范围: `frontend/src/views/data/index.vue`、`frontend/src/views/data/index.test.ts`
- 目标: 把数据看板经营指标矩阵中的"媒介提成"统一为"渠道提成"，与业务域合同（V1 业绩域：`channelCommission` 字段口径 = 渠道提成）保持一致。

## 改动

- `frontend/src/views/data/index.vue` 业务指标矩阵第 532 行：`label: '媒介提成'` → `label: '渠道提成'`。
- `frontend/src/views/data/index.test.ts` 断言同步：`'媒介提成'` → `'渠道提成'`（与 `dashboard-metrics.test.ts`、OrderDetailTab 等下游文案一致）。

## 范围核查

- `frontend` 全量 `grep "媒介提成|媒介"` 结果：
  - `frontend/src/views/data/index.vue` 唯一保留渠道提成 label。
  - `frontend/src/views/data/OrderDetailTab.test.ts` 仍以负向断言 `not.toContain('媒介')` 兜底，兼容新文案。
- 后端合同未改：渠道提成 = `performance_records.channel_commission_cents / 100`，与 label 解耦。
- 不涉及 `harness/SPEC.md`、`harness/CURRENT_STATE.md`、ADR、领域合同。

## 验证

- `npx vitest run src/views/data/index.test.ts`：1 test passed（47ms）。
- `npx vitest run`（frontend 全量）：81 files / 622 tests passed。
- `npx vue-tsc --noEmit -p tsconfig.json`：0 错误。
- 字段口径未变，仅展示文案。

## 结论

- 渠道提成文案已与业务域对齐；不影响指标计算、API 返回、看板双轨金额。
- 后续若再出现"媒介提成"残留视为新回归。
