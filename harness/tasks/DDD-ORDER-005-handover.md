# DDD-ORDER-005 Handover

## Done

- 订单域事件发布边界已收敛到 `OrderDomainEventPublisher` 接口。
- `InProcessOrderDomainEventPublisher` 负责 Outbox / after-commit 本地事件发布。
- `OrderEventPayloadMapper` 负责从 `ColonelsettlementOrder` 生成事件 payload。
- `OrderSyncedEvent` 补齐任务要求的显式订单事实与默认归因字段。

## Verified

- Targeted Maven test command passed: 18 tests, 0 failures, 0 errors.
- Main source was recompiled during the same Maven run.

## Watch Points

- 当前分支上存在 PRODUCT 相关未暂存改动，提交 ORDER-005 时不要混入。
- `harness/reports` 有 10 文件上限，继续生成 evidence 前需保持归档策略。
- 若后续启用 Outbox dispatcher，需验证旧 JSON payload 缺字段时反序列化兼容。

## Next Step

- 运行项目统一 `agent-do.ps1 -Env real-pre -Scope full`。
- 若 Harness full verification 仍失败，按最新 evidence 标记 BLOCKED/PARTIAL，不得写 PASS。
