# Retro — DDD-PHASE0-AUDIT-STATUS-SYNC-001

## 1. 本次修正了什么

- 统一 Phase 0 九项审查为 `DONE_AUDIT` / 总状态 `DONE_AUDIT_COMPLETE`。
- 新建 `00-phase0-audit-summary.md` 作为 Phase 0 总入口。
- 补建 `ddd-audit-analysis-001` 任务卡。
- 修正 `00-task-index` 中 CROSS-DOMAIN 与 ANALYSIS 缺口。
- 更新执行顺序、索引、矩阵、风险门禁、state 页指向 Phase 1。

## 2. 为什么需要 Phase 0 收口

各域审计在不同会话完成，任务索引与 state 仍显示「待审查」，会导致后续 Agent 重复审计或跳过防护测试直接重构。

## 3. 后续为什么必须先进入防护测试

审查仅记录 God Service、跨域穿透与风险，未锁定行为。无测试保护就拆 `OrderSyncService`、业绩公式或寄样状态机会引入 silent regression。

## 4. 后续 Agent 应避免什么

- 不要把 `DONE_AUDIT_COMPLETE` 写成 DDD 重构完成。
- 不要跳过 `DDD-TEST-ORDER-SYNC-001` 等 Phase 1 任务。
- 不要启动 `DDD-FACADE-*` 或 `DDD-PACKAGE-*`  without 对应防护测试。
- 不要用 mock 样本把 real-pre BLOCKED 项写成 PASS。

## 5. Harness 升级

本次为状态同步，无需修改 harness 脚本；建议在 `harness/CURRENT_STATE.md` 后续单独任务中引用 Phase 0 总收口路径。
