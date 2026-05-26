# Outbox 事件机制说明

## 表

复用 `domain_event_outbox`，扩展字段：

- `event_key`（唯一，幂等）
- `headers`
- `max_retry` / `next_retry_at`

消费幂等：`domain_event_consume_log`（配置变更域已有；商品域通过 `event_key` + 分发器去重）。

## 商品域事件类型

- ProductListedEvent / ProductHiddenEvent
- ProductOwnerChangedEvent
- ActivitySyncCompletedEvent / ActivityExtendedEvent
- PartnerSyncCompletedEvent
- ProductDisplayRuleAppliedEvent / ProductForceDisplayChangedEvent

## 写入

业务事务内 `OutboxEventAppender.appendIfAbsent()`，由 `ProductDomainEventPublisher` 封装。

## 分发

`DomainEventDispatcherJob` 扫描 PENDING/FAILED，成功后 `PUBLISHED`；失败指数退避，超限 `DEAD`。

## 管理端

- `GET /api/admin/outbox-events`
- `POST /api/admin/outbox-events/{id}/retry`

## 本地消费

`ProductDomainEventOutboxRouter` 将 Outbox 载荷转为 Spring 本地事件，兼容既有监听器。
