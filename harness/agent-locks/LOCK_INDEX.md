# Agent Lock Index

> Coordinator Agent 维护。Agent 开工前必须检查本索引；有 `in_progress` 锁且路径重叠则停止并输出冲突报告。

| task_id | agent | branch | status | started_at | lock_file |
|---------|-------|--------|--------|------------|-----------|
| DDD-SAMPLE-005-FIX | Sample Agent | feature/ddd/DDD-SAMPLE-005-FIX-sample-agent | in_progress | 2026-06-10T20:10:00 | DDD-SAMPLE-005-FIX-sample-agent.lock.md |

## 规则

1. 新建锁：`harness/agent-locks/<task-id>-<agent-name>.lock.md`
2. 完成后将 `status` 改为 `completed` 并更新本表
3. 共享高风险文件见多 Agent 总控提示词「共享文件规则」

## 当前阻塞（2026-06-10）

- **P0**：`DDD-SAMPLE-005` 循环依赖（`LegacySampleQueryService` ↔ `SampleController`）— 全量 Spring 测试红，须 Sample Agent 串行修复后方可并行 Batch 2+
