# DDD-ANALYTICS-001 — AnalyticsEventConsumer 兼容层

**时间**: 2026-06-10  
**环境**: local / `mvn test`  
**分支**: `feature/auth-system`  
**基线 commit**: `9a5bb555`（提交前）

## 目标

为分析模块建立事件消费入口（兼容层 + 幂等测试），**不切换** `DashboardService` 数据源，不重算业绩归属，不修改业务域数据，不引入外部 MQ。

## 交付物

| 类型 | 路径 |
|------|------|
| 消费入口 | `domain/analytics/application/AnalyticsEventConsumer.java` |
| 聚合服务 | `domain/analytics/application/AnalyticsAggregationService.java` |
| 路由器 | `domain/analytics/application/AnalyticsEventRouter.java` |
| 幂等存储 | `domain/analytics/infrastructure/ProcessedEventStore.java` |
| 内存实现 | `domain/analytics/infrastructure/InMemoryProcessedEventStore.java` |
| 结果类型 | `domain/analytics/application/AggregationUpdateResult.java` |
| 处理器枚举 | `domain/analytics/application/AnalyticsHandlerType.java` |
| 事件类型常量 | `domain/analytics/event/AnalyticsEventTypes.java` |
| 可选事件 | `domain/analytics/event/TalentClaimedEvent.java` |
| Shadow 监听 | `listener/AnalyticsShadowEventListener.java` |
| 单元测试 | `test/.../AnalyticsEventConsumerTest.java` |

## 支持事件与路由

| 事件 | 分析类型 | Handler |
|------|----------|---------|
| `OrderSyncedEvent` | 订单同步 | `ORDER_ESTIMATE_SUMMARY` |
| `PerformanceCalculatedEvent` | 业绩计算 | `PERFORMANCE_SUMMARY` |
| `SampleCreatedEvent` → `SampleSubmittedEvent` | 寄样提交 | `SAMPLE_SUMMARY` |
| `SampleApprovedEvent` | 寄样审批 | `SAMPLE_SUMMARY` |
| `SampleShippedEvent` | 寄样发货 | `SAMPLE_SUMMARY` |
| `SampleCompletedEvent` | 寄样完成 | `SAMPLE_SUMMARY` |
| `ProductListedEvent` | 商品上架 | `PRODUCT_SNAPSHOT` |
| `ProductHiddenEvent` | 商品隐藏 | `PRODUCT_SNAPSHOT` |
| `ActivitySyncCompletedEvent` → `ActivitySyncedEvent` | 活动同步 | `ACTIVITY_SNAPSHOT` |
| `TalentClaimedEvent`（可选） | 达人认领 | `TALENT_SNAPSHOT` |

## 行为说明

1. **默认（开关关闭）**：`AnalyticsShadowEventListener` 调用 `consumeIfShadowEnabled` 直接跳过；`DashboardService` / `OrderSyncedEventListener` / `DashboardPerformanceSummaryService` 路径不变。
2. **Shadow 开启**：`ddd.refactor.enabled=true` 且 `ddd.refactor.analytics-shadow.enabled=true` 时，异步监听 Spring 事件并走 `AnalyticsEventConsumer`。
3. **幂等**：`ProcessedEventStore` 按 `eventId` 去重；重复消费返回 `duplicateSkipped=true`。
4. **聚合层**：`AnalyticsAggregationService` 当前仅 debug 日志 + 调用计数，**不写** `dashboard_performance_daily` 等表。
5. **processed_events**：本任务使用 `InMemoryProcessedEventStore`；持久化表与 Outbox 联动留待 `ddd.refactor.outbox` 阶段（仅新增 migration，不在本任务建表）。

## 构建与测试

### 分析 + Dashboard 定向测试

```text
mvn test -Dtest=AnalyticsEventConsumerTest,DashboardServiceTest,DashboardPerformanceSummaryServiceTest,DashboardControllerTest
```

| 套件 | 结果 |
|------|------|
| `AnalyticsEventConsumerTest` | PASS (5/5) |
| `DashboardServiceTest` | PASS |
| `DashboardPerformanceSummaryServiceTest` | PASS |
| `DashboardControllerTest` | PASS |

覆盖项：

- 同一 `eventId` 重复消费只处理一次
- 业绩事件 → `PERFORMANCE_SUMMARY`
- 订单事件 → `ORDER_ESTIMATE_SUMMARY`
- 寄样事件 → `SAMPLE_SUMMARY`
- 商品事件 → `PRODUCT_SNAPSHOT`

### 全量后端（`mvn test`）

- **结论**: PARTIAL（本任务范围外既有失败）
- `Tests run: 1916, Failures: 16, Errors: 91`
- 失败主因：工作区 `SampleController` / `LegacySampleQueryService` 循环依赖导致 Spring 上下文加载失败；`DddConfig003ConfigRoutingTest` 等并行 WIP。
- 分析兼容层与 Dashboard 定向套件全部 PASS。

## 未改动（按任务约束）

- `DashboardService` 查询逻辑与数据源策略
- 业绩归属重算、`OrderSyncedEventListener` 现有副作用
- 业务域表数据写入
- 外部 MQ

## 剩余风险

1. Shadow 监听器与现有监听器并行订阅同一 Spring 事件；开关默认 off，需 Shadow 对账阶段再验证吞吐与顺序。
2. `SampleSubmittedEvent` 映射自 `SampleCreatedEvent`；若后续独立事件类型需同步 router。
3. `InMemoryProcessedEventStore` 进程重启后幂等状态丢失；Outbox 阶段需换持久化实现。

## 结论

**PASS（任务范围内）** — Analytics 事件消费兼容层、路由与幂等测试已落地；Dashboard 数据源未切换；全量套件受仓库并行 WIP 影响记为 PARTIAL。
