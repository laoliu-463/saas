# DDD-OUTBOX-001 订单同步事件 Outbox 路由报告

时间：2026-06-12
任务：Batch4 — OrderSyncedEvent 接入 Outbox 开关路径

## 变更摘要

| 原调用点 | 新调用点 | 开关 | 回退 |
|---------|---------|------|------|
| `OrderSyncPersistenceService` 事务后直发 Spring 事件 | Outbox 写入 + `DomainEventDispatcherJob` 重发布 | `ddd.refactor.outbox.enabled` | 关闭开关保持 afterCommit 直发 |

## 新增/修改

- `OrderDomainEventTypes` / `OrderDomainEventPublisher` / `OrderDomainEventOutboxRouter`
- `OutboxEventAppender.AGGREGATE_ORDER`
- `DomainEventDispatcherJob` 增加订单路由
- `OrderSyncPersistenceService` 开关分流（**未改** `OrderSyncService`）

## 验证

- 定向单测：PASS（22 项）
- 待 agent-do 定稿

## 结论

阶段性 **PASS**
