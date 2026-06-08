# Retro - DDD-AUDIT-ORDER-001

## 1. 本次做对了什么
- **严格遵守 A 类只读门禁**: 没有修改任何后端 Java 代码或触发任何数据库写回，保证了极高重构 Phase 0 安全性。
- **详实的链路映射与梳理**: 搞清了 6468 / 2704 双轨数据的落库和各自的水位 key/lock 控制策略，为后续剥离 Gateway 提供了扎实的事实源。

## 2. 本次发现了什么订单域风险
- **上游 SDK 模型未隔离**: `RealDouyinOrderGateway` 返回的 `OrderItem` raw payload 仍是一个 Map，这导致 Service 层充满了对 `estimated_commission`、`colonel_buyin_id` 等具体 Json 嵌套键名的硬编码解析。
- **上帝服务庞大**: `OrderSyncService` 既是定时同步状态机的管理者，又承担了从 SDK 转换为本地实体的解析者，且内部还有重试和熔断，违反了单一职责原则。

## 3. 哪些结论仍需后续测试确认
- **归因重叠与去重锁的抗并发性能**: `order_sync_dedup_claim` 主键抢占表和乐观锁在并发拉取同一订单时的精确去重行为，需要等接下来的回归单测 `DDD-TEST-ORDER-SYNC-001` 来落地锁定。

## 4. 下一步为什么做业绩域或寄样域审查
- **业绩域严重依赖订单事实**: 业绩域计算逻辑完全取决于订单实付与结算双轨金额。在理清订单如何落库与触发 afterCommit 事件后，需要接着只读分析业绩域如何消费 `OrderSyncedEvent` 事件并规避 settle_amount 计算回退污染，因此下一步推荐进行 `DDD-AUDIT-PERFORMANCE-001` (业绩域只读审查)。

## 5. 后续 Agent 应避免什么
- 绝对不要在没有单测保护的前提下直接拆分 `OrderSyncService` 或提炼 Facade，必须一步步执行，先建立测试，再逐步解耦。
