# Agent Lock Index

> Coordinator Agent 维护。Agent 开工前必须检查本索引；有 `in_progress` 锁且路径重叠则停止并输出冲突报告。

| task_id | agent | branch | status | started_at | lock_file |
|---------|-------|--------|--------|------------|-----------|
| DDD-SAMPLE-005-FIX | Sample Agent | feature/ddd/DDD-SAMPLE-005-FIX-sample-agent | completed | 2026-06-10T20:10:00 | DDD-SAMPLE-005-FIX-sample-agent.lock.md |

## 规则

1. 新建锁：`harness/agent-locks/<task-id>-<agent-name>.lock.md`
2. 完成后将 `status` 改为 `completed` 并更新本表
3. 共享高风险文件见多 Agent 总控提示词「共享文件规则」

## 当前阻塞（2026-06-12）

- ~~**P0**：`DDD-SAMPLE-005-FIX` 循环依赖~~ **已解除**（`ColonelSaasApplicationTests` 绿）
- ~~**P0**：`DDD-CONFIG-003-FIX`~~ **已绿**（`DddConfig003ConfigRoutingTest`）
- **P1 进行中**：`DDD-ORDER-002`（Policy + `OrderAmountMappingRouter`，见 `runtime/qa/out/ddd-order-002-amount-policy-20260612.md`）
- **P2 技术债**：`OrderSyncServiceTest` 6 项与 institute settlement gateway 路径不一致
