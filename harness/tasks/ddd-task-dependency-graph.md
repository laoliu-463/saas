# DDD Task Dependency Graph

```mermaid
flowchart TD
  B001[DDD-BASE-001 开关] --> ALL[所有 REPLACE 任务]
  B003[DDD-BASE-003 依赖护栏] --> ALL
  B004[DDD-BASE-004 包骨架] --> FACADE[Batch1 Facade]
  FACADE --> POLICY[Batch2 Policy]
  POLICY --> REPLACE[Batch3 调用替换]
  REPLACE --> EVENT[Batch4 Outbox/Analytics]
  EVENT --> CLEAN[Batch5 Clean]

  U001[DDD-USER-001 Facade] --> U003[DDD-USER-003 扩展消费]
  C001[DDD-CONFIG-001 Facade] --> C003[DDD-CONFIG-003 路由]
  O001[DDD-ORDER-001 AppService] --> O002[DDD-ORDER-002 AmountPolicy]
  P002[DDD-PRODUCT-002 DisplayPolicy] --> P001[DDD-PRODUCT-001 Facade 可选]
  S005[DDD-SAMPLE-005 Query拆分] --> S005F[DDD-SAMPLE-005-FIX 循环依赖]
  S005F --> B002[DDD-BASE-002 基线测试绿]
  A001[DDD-ANALYTICS-001 Consumer] --> A002[DDD-ANALYTICS-002 Shadow]
```

## 串行链（Integration 合并顺序）

1. `DDD-SAMPLE-005-FIX`（必须先绿 `ColonelSaasApplicationTests`）
2. `DDD-CONFIG-003-FIX`
3. `DDD-ORDER-002`
4. Facade 并行批次合并：USER-003 / PRODUCT-001 / TALENT-001 / PERF-001
5. Policy 批次：PERF-002 / SAMPLE-006
6. Batch3 调用替换（每次一个方向）
7. Outbox / processed_events migration（Infra 牵头）

## 文件冲突矩阵（高风险）

| 文件 | 可能冲突 Agent |
|------|----------------|
| `OrderSyncService.java` | Order Agent only |
| `SampleController.java` / `LegacySampleQueryService.java` | Sample Agent only |
| `CommissionService.java` / `ProductService.java` | Config + Performance；须串行 |
| `cross-domain-mapper-legacy-whitelist.txt` | Architecture Guard 审批后任一 Agent |
| `DddRefactorProperties.java` | Infra Agent only |
| `application.yml` | Infra Agent only |
