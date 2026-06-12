# 多 Agent DDD 重构提示词索引

> 各 Agent 独立提示词已拆分到 `harness/prompts/agents/` 目录。本文件仅作索引。
> 完整历史版本归档于 `harness/reports/archive/20260610/multi-agent-ddd-prompts-full.md`。

## Agent 提示词列表

| 编号 | Agent | 文件 |
|---|---|---|
| 00 | Coordinator | [00-coordinator.md](../prompts/agents/00-coordinator.md) |
| 01 | Architecture Guard | [01-architecture-guard.md](../prompts/agents/01-architecture-guard.md) |
| 02 | User | [02-user.md](../prompts/agents/02-user.md) |
| 03 | Config | [03-config.md](../prompts/agents/03-config.md) |
| 04 | Product | [04-product.md](../prompts/agents/04-product.md) |
| 05 | Talent | [05-talent.md](../prompts/agents/05-talent.md) |
| 06 | Sample | [06-sample.md](../prompts/agents/06-sample.md) |
| 07 | Order | [07-order.md](../prompts/agents/07-order.md) |
| 08 | Performance | [08-performance.md](../prompts/agents/08-performance.md) |
| 09 | Analytics | [09-analytics.md](../prompts/agents/09-analytics.md) |
| 10 | Frontend | [10-frontend.md](../prompts/agents/10-frontend.md) |
| 11 | Test | [11-test.md](../prompts/agents/11-test.md) |
| 12 | Infra | [12-infra.md](../prompts/agents/12-infra.md) |
| 13 | Integration | [13-integration.md](../prompts/agents/13-integration.md) |
| 14 | Review | [14-review.md](../prompts/agents/14-review.md) |

## 配套文档

- 路线图：[DDD_OPTIMIZATION_ROADMAP.md](../plans/DDD_OPTIMIZATION_ROADMAP.md)
- 任务矩阵：[DDD_DOMAIN_TASK_MATRIX.md](../plans/DDD_DOMAIN_TASK_MATRIX.md)
- 领域地图：[DOMAIN_MAP.md](../DOMAIN_MAP.md)
- 任务看板：[ddd-multi-agent-board.md](../tasks/ddd-multi-agent-board.md)
- 依赖图：[ddd-task-dependency-graph.md](../tasks/ddd-task-dependency-graph.md)
- 锁索引：[LOCK_INDEX.md](../agent-locks/LOCK_INDEX.md)

## 多 Agent 协作核心规则

1. 每个 Agent 一次只执行一个任务，只修改允许范围的文件。
2. 禁止越界修改、顺手重构、顺手修复。
3. 禁止修改公网 API 路径/入参/出参，除非任务明确要求。
4. 每个任务必须独立测试、独立报告、独立 commit。
5. 任何 Agent 不允许直接合并主分支，由 Integration Agent 统一处理。
6. 同一文件同一时间只能分配给一个 Agent。
7. 开工前创建 lock 文件，完成后生成 handover 文档。
