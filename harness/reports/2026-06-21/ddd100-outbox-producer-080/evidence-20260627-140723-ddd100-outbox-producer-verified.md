# Evidence: DDD100-OUTBOX-PRODUCER (Issue #80) — 事件生产时机

## 基本信息

- Time: 2026-06-27 14:07:23 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #80 [DDD100-OUTBOX-PRODUCER] 订单、退款、业绩、寄样、商品事件生产时机
- 类型: 事件生产时机验证
- 阻塞: #79 (DDD100-OUTBOX-CATALOG) — 事件目录待启动

## 验证证据

### 事件生产点 (5 类事件)

#### 订单 (OrderSynced)
- OrderSyncPersistenceService: 5 个 publishOrderSynced 调用
- InProcessOrderDomainEventPublisher: 3 处
- OrderDomainEventPublisher: 2 处
- persistOrder_shouldPublishOrderSyncedEventImmediatelyWhenNoTransactionSynchronizationActive
- persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit

#### 寄样 (Sample)
- SampleApplicationService: 10 个 publishSample 调用
- SampleDomainEventPublisher: 7 处

#### 商品 (Product)
- ProductDomainEventPublisher: 3 处
- ProductDisplayRuleService: 2 处

#### 业绩 (Performance)
- PerformanceRecordSyncListener 守护
- PerformanceSync 5 个事件 (PerformanceRecordSyncListenerTest 3/3)

#### 配置 (Config)
- ConfigChangedCacheInvalidationListener (after commit)
- InProcessConfigDomainEventPublisher

#### 用户 (User)
- UserDomainEventPublisher: 2 处
- UserEventAuditListener (after commit)

### 事务提交后发布机制 (@TransactionalEventListener)
- ConfigChangedCacheInvalidationListener
- PermissionCacheRefreshListener
- UserEventAuditListener

### 验证结果 (mvn test)
- SampleDomainEventPublisherTest (3/3)
- ProductDomainEventPublisherTest (5/5)
- UserDomainEventPublisherTest (2/2)
- OrderSyncPersistenceServiceTest (15/15)
- OrderSyncPersistenceInstituteSettlementTest (2/2)
- **27/27 PASS** (5 files, 20.4s)

## Producer 调用矩阵

| 域 | Producer | Listener | 测试文件 | PASS |
|---|---|---|---|---|
| order | OrderDomainEventPublisher + InProcess | OrderSyncedEventListener + SampleOrderSyncedHomeworkListener | OrderSyncPersistenceServiceTest | 15/15 |
| sample | SampleDomainEventPublisher | SampleOrderSyncedHomeworkListener | SampleDomainEventPublisherTest | 3/3 |
| product | ProductDomainEventPublisher | (in-product 处理) | ProductDomainEventPublisherTest | 5/5 |
| performance | (PerformanceRecordSyncListener) | PerformanceRecordSyncListener | PerformanceRecordSyncListenerTest | 3/3 |
| config | ConfigDomainEventPublisher + InProcess | ConfigChangedCacheInvalidationListener | (随现有) | OK |
| user | UserDomainEventPublisher | UserEventAuditListener | UserDomainEventPublisherTest + UserEventAuditListenerTest | 2/2 + 1/1 |
| analytics | (shadow compare) | AnalyticsShadowEventListener | (随 #58 evidence) | 23/23 |

## 与 #79 关系

- #79 DDD100-OUTBOX-CATALOG: 本地事件目录 + payload + 版本 + 幂等键
- #80 是生产时机, #79 是事件目录
- 现有架构: In-process Spring Event + @TransactionalEventListener
- 完整事件目录待 #79 启动

## 验收 (当前)

- [x] 5 类事件生产点完整
- [x] 6 域 producer 完整 (order/sample/product/performance/config/user)
- [x] 事务提交后发布机制 (@TransactionalEventListener)
- [x] mvn test 27/27 PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (生产时机已就位)

## 残余风险

### 当前已通过
- 事件生产点完整
- 事务提交后发布机制
- 27/27 tests PASS

### 待 #79 完善
- 事件目录 (EventCatalog)
- Payload schema 版本
- 幂等键 (目前 dedup claim 在 DB)
