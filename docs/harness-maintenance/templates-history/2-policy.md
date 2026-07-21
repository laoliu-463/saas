# Batch 2 — Policy（业务规则抽取）

## 目标

把 Facade 后面的**业务规则**抽到独立 `*Policy` 类：
- `OrderAmountMapperPolicy`（**当前 WIP**，由 Order Agent 跟到绿）
- `PerformanceMoneyPolicy`（双轨金额）
- `TalentClaimPolicy`（认领冲突）
- `SampleStateMachine`（状态机，依赖 SAMPLE-005）
- `ProductQuickSamplePolicy`（如需）
- `CommissionPolicy`（**业绩域独占** `CommissionService.java`）

**只抽 Policy，不改业务结果**。Policy 是"原 Service 的方法分解"，不是"新规则"。

## 入口任务

| task_id | Agent | 状态 | 备注 |
|---|---|---|---|
| DDD-ORDER-002 | Order | WIP | `OrderAmountMapperPolicy` |
| DDD-PERF-002 | Performance | READY | `PerformanceMoneyPolicy` |
| DDD-TALENT-002 | Talent | READY | `TalentClaimPolicy` |
| DDD-SAMPLE-006 | Sample | 等 SAMPLE-005 | `SampleStateMachine` |
| DDD-PRODUCT-002 | Product | 等 PRODUCT-001 | `ProductQuickSamplePolicy` |
| DDD-CONFIG-003-FIX | Config | READY | 2 baseline 失败独立修复 |
| DDD-COMMISSION-001 | Performance | 等 PERF-001 | 改 `CommissionService.java` |

## 文件冲突矩阵

| 文件 | 锁方 | 并行？ |
|---|---|---|
| `OrderAmountMapperPolicy.java` | Order | 独占 |
| `PerformanceMoneyPolicy.java` | Performance | 独占 |
| `TalentClaimPolicy.java` | Talent | 独占 |
| `SampleStateMachine.java` | Sample | 独占 |
| `CommissionService.java` | Performance | **与 Config 串行**（历史合同） |
| `ProductService.java` | Performance + Config | **性能 + 配置串行** |

**文件冲突**：
- Performance Agent 占 `CommissionService.java` 与 `ProductService.java` 时，Config Agent 必须等

## 启动提示词

```text
我是 Coordinator。任务：启动 Batch 2（Policy 抽取）。
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 读 `docs/harness-maintenance/tasks-history/ddd-task-dependency-graph.md` 串行链
3. 启动 Order Agent 跟 WIP DDD-ORDER-002（不重启）
4. 启动 Config Agent 跟 DDD-CONFIG-003-FIX
5. 启动 Performance Agent 起 DDD-PERF-002
6. 启动 Talent Agent 起 DDD-TALENT-002
7. SAMPLE / PRODUCT 任务等前置
8. 每个 Agent 按对应提示词执行
9. 收齐 commit + 测试 + 报告
10. 更新看板
```

## 退出条件

- [ ] 所有 Policy 抽完 + 单测
- [ ] `CommissionService.java` 性能 + 配置串行合并
- [ ] `mvn test` 全绿
- [ ] 旧 Service 行为 1:1 保留

## 串行依赖

- Batch 1 全完
- Performance ↔ Config 串行
- SAMPLE 等 SAMPLE-005
- PRODUCT 等 PRODUCT-001
