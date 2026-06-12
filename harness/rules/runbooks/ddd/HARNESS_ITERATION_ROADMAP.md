# Harness Iteration Roadmap

> Harness 自身的迭代路线图。区别于 `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`（业务 DDD 路线图）。

## 0. 当前版本

- `HARNESS_CHANGELOG.md` 最新条目见该文件。
- 最近一次 Harness 重大治理：GIT-HARNESS-001（2026-06-03 15:00）。

## 1. 短期（HARNESS-DEBT-GOVERNANCE-ITERATION 之后 1-2 个迭代）

| 任务 | 标题 | 范围 | DEBT | 状态 |
| --- | --- | --- | --- | --- |
| HARNESS-DEBT-GC-001 | 清理 12 个 ad-hoc log + 写 .gitignore | docs + .gitignore | DEBT-013 | open |
| HARNESS-AGENT-DO-HARDEN | agent-do.ps1 增 `-Scope harness` + safety-check 扩展 scope | docs + scripts | DEBT-020/22 | open |
| HARNESS-ENV-CHEATSHEET-V2 | 环境速查表升级（远端 SSH alias / 远端目录 / 健康 URL） | docs | DEBT-008/19/24 | open |
| HARNESS-REPORT-ROTATION-001 | reports 超过 100 时启动归档 | docs | DEBT-014 | open（条件触发） |

## 2. 中期（业务 DDD 路线图完成后）

| 任务 | 标题 | 范围 | 依赖 |
| --- | --- | --- | --- |
| HARNESS-EVIDENCE-AUTO-001 | 自动生成 evidence / retro 模板填充 | scripts | 业务 DDD 稳定 |
| HARNESS-DIRTY-AUTO-001 | "未变更文件"自动化检测 | scripts | safety-check scope 扩展 |
| HARNESS-PROMPT-OPT-001 | Agent 提示词 / skill / 模板自动化优化 | docs + skills | retro 反馈循环稳定 |

## 3. 长期（V1 闭环后）

| 任务 | 标题 | 范围 | 依赖 |
| --- | --- | --- | --- |
| HARNESS-V2-PLAN-001 | V2 范围 + 新 harness 子系统设计 | docs | V1 验收通过 |
| HARNESS-OUTBOX-EVT-001 | 领域事件 + 异步任务子系统 | docs + scripts | 业务 Outbox 稳定 |

## 4. 与其他路线图关系

| 路线图 | 关注 |
| --- | --- |
| `DDD_OPTIMIZATION_ROADMAP.md` | 业务 DDD 优化 |
| `DDD_DOMAIN_TASK_MATRIX.md` | 业务领域任务矩阵 |
| `HARNESS_ITERATION_ROADMAP.md`（本文件） | Harness 自身迭代 |
| `HARNESS_DEBT.md` | 债务登记 |
| `HARNESS_CHANGELOG.md` | 变更日志 |
| `CURRENT_STATE.md` | 项目当前状态 |

## 5. 升级触发条件（写新路线图项的判断标准）

满足以下任一条件即可在本文件追加新条目：

- 出现新 scope 但 `scope-command-matrix.md` 没有覆盖。
- Agent 重复试探同类命令 3 次以上。
- 业务 DDD 路线图与本路线图发生依赖变化。
- `state/HARNESS_DEBT.md` 中某条 DEBT 关闭后衍生新债务。

## 6. 关联文档

- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
- `harness/state/HARNESS_DEBT.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/runbooks/debt-governance.md`
