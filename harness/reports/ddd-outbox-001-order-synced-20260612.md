# DDD-OUTBOX-001 璁㈠崟鍚屾浜嬩欢 Outbox 璺敱鎶ュ憡

鏃堕棿锛?026-06-12
浠诲姟锛欱atch4 鈥?OrderSyncedEvent 鎺ュ叆 Outbox 寮€鍏宠矾寰?
## 鍙樻洿鎽樿

| 鍘熻皟鐢ㄧ偣 | 鏂拌皟鐢ㄧ偣 | 寮€鍏?| 鍥為€€ |
|---------|---------|------|------|
| `OrderSyncPersistenceService` 浜嬪姟鍚庣洿鍙?Spring 浜嬩欢 | Outbox 鍐欏叆 + `DomainEventDispatcherJob` 閲嶅彂甯?| `ddd.refactor.outbox.enabled` | 鍏抽棴寮€鍏充繚鎸?afterCommit 鐩村彂 |

## 鏂板/淇敼

- `OrderDomainEventTypes` / `OrderDomainEventPublisher` / `OrderDomainEventOutboxRouter`
- `OutboxEventAppender.AGGREGATE_ORDER`
- `DomainEventDispatcherJob` 澧炲姞璁㈠崟璺敱
- `OrderSyncPersistenceService` 寮€鍏冲垎娴侊紙**鏈敼** `OrderSyncService`锛?
## 楠岃瘉

- 瀹氬悜鍗曟祴锛歅ASS锛?2 椤癸級
- 寰?agent-do 瀹氱

## 缁撹

闃舵鎬?**PASS**
