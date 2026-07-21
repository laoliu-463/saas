# Batch 0 — 准备（Prepare）

## 目标

让 DDD 重构**具备可并行、可审计、可回滚**的基础设施：
- 看板运转（Coordinator 拥有）
- 锁索引运转（所有 Agent 共用）
- 13 个开关就绪（Infra 拥有）
- 架构守卫就绪（Architecture Guard 拥有）
- 现有 10 个 characterization 测试就绪（Test 拥有）

## 入口任务

| task_id | Agent | 状态 | commit / 说明 |
|---|---|---|---|
| DDD-BASE-001 | Infra | DONE | `runtime/qa/out/ddd-base-001-refactor-switches.md` |
| DDD-BASE-002 | Test | PARTIAL | `runtime/qa/out/ddd-base-002-characterization.md`（受 SAMPLE-005-FIX 阻塞） |
| DDD-BASE-003 | Coordinator | DONE | 看板 / 依赖图 |
| DDD-BASE-004 | Infra | DONE | `runtime/qa/out/ddd-base-004-package-structure.md` |
| DDD-AGENT-001 | Coordinator | READY | 多 Agent 提示词包 + 任务卡 + 锁索引（本次） |

## 启动提示词

```text
我是 Coordinator。任务：启动 Batch 0。
请执行：
1. 读 `docs/harness-maintenance/tasks-history/ddd-multi-agent-board.md` + `docs/harness-maintenance/tasks-history/ddd-task-dependency-graph.md`
2. 确认 DDD-BASE-001/003/004 已 DONE；DDD-BASE-002 PARTIAL 由 Test Agent 跟踪
3. 启动 4 个 Agent 任务卡：
   - 看板更新（Coordinator 自管）
   - 锁索引建立（Coordinator 自管）
   - 13 开关就绪（Infra Agent 跟踪）
   - Characterization 测试就绪（Test Agent 跟踪）
4. 输出 Batch 0 启动报告 `runtime/qa/out/ddd-batch-0-prepare.md`
5. 不 push；不合并；不改业务代码
```

## 退出条件

- [ ] 4 个入口任务全部 DONE 或 PARTIAL（带独立修复任务）
- [ ] LOCK_INDEX.md 索引已建
- [ ] 看板、依赖图、提示词包一致

## 串行依赖

无。Batch 0 是所有后续批次的**前置**。
