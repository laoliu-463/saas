# Batch 1 — Facade（门面层抽取）

## 目标

每个业务域抽出 `XxxDomainFacade` 作为**唯一对外入口**：
- 商品域：`ProductDomainFacade`（已部分就绪）
- 订单域：`OrderDomainFacade`
- 寄样域：`SampleDomainFacade`
- 达人域：`TalentDomainFacade`
- 业绩域：`PerformanceQueryFacade`
- 用户域：`UserDomainFacade`（已完成）
- 配置域：`ConfigDomainFacade`（已完成）

**只抽 Facade，不搬业务**。原 Service 仍可写，原 Controller 仍可直调，Facade 处于"可选调用"状态。

## 入口任务

| task_id | Agent | 状态 | 备注 |
|---|---|---|---|
| DDD-USER-003 | User | DONE | `UserDomainFacade` |
| DDD-CONFIG-001 | Config | DONE | `ConfigDomainFacade` |
| DDD-PRODUCT-001 | Product | READY | `ProductDomainFacade` |
| DDD-ORDER-001 | Order | READY | `OrderDomainFacade` |
| DDD-TALENT-001 | Talent | READY | `TalentDomainFacade` |
| DDD-PERF-001 | Performance | READY | `PerformanceQueryFacade` |
| DDD-SAMPLE-001 | Sample | P0 阻塞 | 待 SAMPLE-005-FIX 完成 |

## 文件冲突矩阵

| 文件 | 锁方 | 并行？ |
|---|---|---|
| `ProductDomainFacade.java` | Product | 独占 |
| `OrderDomainFacade.java` | Order | 独占 |
| `SampleDomainFacade.java` | Sample | 等 SAMPLE-005 |
| `TalentDomainFacade.java` | Talent | 独占 |
| `PerformanceQueryFacade.java` | Performance | 独占 |
| `UserDomainFacade.java` | User | 独占 |
| `ConfigDomainFacade.java` | Config | 独占 |

**无文件冲突**，可全并行。

## 启动提示词

```text
我是 Coordinator。任务：启动 Batch 1（Facade 抽取并行批次）。
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 对每个 READY 任务，按 `harness/agents/prompts/<role>.md` 启动 Agent
3. 每个 Agent 必须：
   - 建 lock
   - 拉 `feature/auth-system` 起点
   - TDD
   - 跑 `mvn test`
   - 写报告 + handover
   - commit
   - 不 push / 不合并
4. 用户域 / 配置域已完成，复用既有报告范式
5. 收齐 7 个分支的 commit hash + 测试统计
6. 更新看板
```

## 退出条件

- [ ] 7 个 Facade 全部抽出 + 各自单测
- [ ] 7 个分支 status = completed + Review Agent PASS
- [ ] Integration Agent 按无冲突顺序合并到 `feature/auth-system`
- [ ] `mvn test` 全绿
- [ ] `npm run e2e:v1-p0` 通过（mock 基线）

## 串行依赖

- DDD-SAMPLE-005-FIX 必须先于 DDD-SAMPLE-001
- Batch 1 全部完成才能进入 Batch 2
