# Evidence: DDD100-OUTBOX-CATALOG (Issue #79) — 本地事件目录、payload、版本和幂等键

## 基本信息

- Time: 2026-06-27 14:08:50 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #79 [DDD100-OUTBOX-CATALOG] 本地事件目录、payload、版本和幂等键
- 类型: 事件目录 + payload + 幂等键
- 阻塞: #49 ORDER-VERIFY (已完成) / #55 PERF-VERIFY (已完成) / #78 SAMPLE-E2E (待启动)

## 事件目录 (本地, 6 域)

### 基础设施 (domain/event/)
- DomainEventOutbox (实体, 持久化 outbox)
- DomainEventOutboxMapper
- DomainEventOutboxService (11/11 PASS)
- OutboxEventAppender
- DomainEventConsumeLog (消费日志 + 幂等键)
- DomainEventConsumeLogMapper
- DomainEventStatus enum
- DomainEventDispatcherJob (9/9 PASS, 调度)

### Order 域 (domain/order/event/)
- OrderSyncedEvent
- OrderStatusChangedEvent
- OrderEventPayloadMapper (1/1 PASS)
- OrderDomainEventPublisher + InProcess

### Sample 域 (8 个事件)
- SampleCreatedEvent / SampleApprovedEvent / SampleRejectedEvent
- SampleShippedEvent / SampleSignedEvent
- SampleCompletedEvent / SampleClosedEvent
- SampleDomainEventPublisher (3/3 PASS)
- SampleDomainEventOutboxRouter

### Product 域 (5 个事件)
- ActivitySyncCompletedEvent / PartnerSyncCompletedEvent
- ProductHiddenEvent / ProductListedEvent / ProductOwnerChangedEvent
- ProductDomainEventPublisher (5/5 PASS)
- ProductDomainEventOutboxRouter

### User 域 (4 个事件)
- UserCreatedEvent / UserDisabledEvent / UserGroupChangedEvent
- RolePermissionUpdatedEvent
- UserDomainEventPublisher (2/2 PASS)
- UserDomainEventHeaders (header 版本)

### Talent 域 (2 个事件)
- ExclusiveTalentActivatedEvent / ExclusiveTalentExpiredEvent
- ExclusiveTalentDomainEventPublisher + Default

### Config 域
- ConfigChangedApplicationEvent
- ConfigUpdatedEvent
- ConfigDomainEventPublisher + InProcess

### Analytics 域
- AnalyticsEventTypes
- TalentClaimedEvent

## Payload + 版本 + 幂等键

### Payload 序列化
- OrderEventPayloadMapper: 订单事件 payload 转换
- 每个 Event 是 record, 自动生成 toString/equals/hashCode

### Header (版本)
- UserDomainEventHeaders: 事件 header + 版本
- 包级别 version: 通过 SemVer 管理

### 幂等键
- DomainEventConsumeLog: 消费日志 (event_id + consumer 唯一键)
- OrderSyncDedupClaimMapper (dedup claim): DB 层幂等
- DashboardPerformanceSummaryServiceTest: CrossDay dedup (#49 evidence)

## 验证证据 (mvn test)

| 测试 | PASS |
|---|---|
| DomainEventOutboxServiceTest | 11/11 |
| OrderEventPayloadMapperTest | 1/1 |
| SampleDomainEventPublisherTest | 3/3 |
| ProductDomainEventPublisherTest | 5/5 |
| UserDomainEventPublisherTest | 2/2 |
| DomainEventDispatcherJobTest | 9/9 |
| **总计** | **31/31 PASS** |

## 验收 (当前)

- [x] 6 域事件完整 (order/sample/product/user/talent/config)
- [x] Outbox + ConsumeLog 基础设施
- [x] DomainEventOutboxService (持久化)
- [x] DomainEventDispatcherJob (调度)
- [x] Payload Mapper (OrderEventPayload)
- [x] Header + 版本 (UserDomainEventHeaders)
- [x] 幂等键 (ConsumeLog + DedupClaim + CrossDay dedup)
- [x] mvn test 31/31 PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (本地事件目录完整)

## 残余风险

### 当前已通过
- 事件目录完整 (6 域)
- Payload + Header + 幂等键基础设施
- 31/31 tests PASS

### 改进项
- 完整事件 schema 版本管理 (Header 已建, 但全局 catalog 待完善)
- 跨域事件依赖图 (DomainEventRouter 已建)
