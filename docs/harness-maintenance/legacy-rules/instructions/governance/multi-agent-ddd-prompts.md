# 多 Agent DDD 重构提示词索引

> 旧版独立提示词目录 `harness/prompts/agents/` 当前不存在；执行多 Agent DDD 任务时，以本文件下列现行 Harness 规则和模板拼装提示词。
> 历史完整版本如需追溯，应优先在 `Git history/` 中检索，不得把旧路径当作当前执行入口。

## Agent 输入源列表

| Agent | 当前输入源 |
|---|---|
| Coordinator | [DDD_OPTIMIZATION_ROADMAP.md](../../runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md)、[DDD_DOMAIN_TASK_MATRIX.md](../../runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md) |
| Architecture Guard | [ddd-boundary-check.skill.md](../../skills/ddd/ddd-boundary-check.skill.md)、[domains-map.md](../../governance/domains-map.md) |
| User | [user-domain.md](../domain/user-domain.md) |
| Config | [config-domain.md](../domain/config-domain.md) |
| Product | [product-domain.md](../domain/product-domain.md) |
| Talent | [talent-domain.md](../domain/talent-domain.md) |
| Sample | [sample-domain.md](../domain/sample-domain.md) |
| Order | [order-domain.md](../domain/order-domain.md) |
| Performance | [performance-domain.md](../domain/performance-domain.md) |
| Analytics | [analytics-module.md](../domain/analytics-module.md) |
| Frontend | [frontend-ux.skill.md](../../skills/workflow/frontend-ux.skill.md) |
| Test | [test-validation.md](../../runbooks/governance/test-validation.md) |
| Infra | [scope-command-matrix.md](../../runbooks/governance/scope-command-matrix.md)、[docker-compose-operations.md](../../runbooks/governance/docker-compose-operations.md) |
| Integration | [code-review.skill.md](../../skills/workflow/code-review.skill.md)、[closeout-and-gc.md](../../runbooks/governance/closeout-and-gc.md) |
| Review | [code-review.skill.md](../../skills/workflow/code-review.skill.md) |

## 配套文档

- 路线图：[DDD_OPTIMIZATION_ROADMAP.md](../../runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md)
- 任务矩阵：[DDD_DOMAIN_TASK_MATRIX.md](../../runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md)
- 领域地图：[domains-map.md](../../governance/domains-map.md)
- 任务看板：[ddd-multi-agent-board.md](../../../tasks/ddd-multi-agent-board.md)
- 依赖图：[ddd-task-dependency-graph.md](../../../tasks/ddd-task-dependency-graph.md)
- 锁索引：[INDEX.md](../../locks/INDEX.md)

## 多 Agent 协作核心规则

1. 每个 Agent 一次只执行一个任务，只修改允许范围的文件。
2. 禁止越界修改、顺手重构、顺手修复。
3. 禁止修改公网 API 路径/入参/出参，除非任务明确要求。
4. 每个任务必须独立测试、独立报告、独立 commit。
5. 任何 Agent 不允许直接合并主分支，由 Integration Agent 统一处理。
6. 同一文件同一时间只能分配给一个 Agent。
7. 开工前创建 lock 文件，完成后生成 handover 文档。
