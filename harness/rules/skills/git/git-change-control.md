# Git Change Control

> 本文件定义 Harness 中所有 Git 相关强约束。
> 详细 Gate 定义见子文件。

## 1. 总原则

1. **任务开始前**：执行 Git Intake Gate → [详情](git-change-control.intake.md)
2. **任务执行中**：只能改当前任务 Allowed Change Set 范围
3. **提交前**：执行 Staged Scope Gate → [详情](git-change-control.commit.md)
4. **推送前**：执行 Push Gate → [详情](git-change-control.commit.md)
5. **部署前**：执行 Deploy Commit Gate → [详情](git-change-control.commit.md)
6. **任务结束前**：执行 Git Exit Gate → [详情](git-change-control.exit.md)

GitHub 协作采用固定链路：`Issue → 独立 worktree/短期分支 → Draft PR → CI → 评审 → 串行合并`。普通任务禁止直接推送 `main` / `release/real-pre`、直接合并或直接部署；平台 Merge Queue 可用时由队列执行，否则由唯一合并控制器逐个合并。

## 2. 禁止命令

```powershell
# 以下默认禁止
git add .
git add -A
git add harness/
git add backend/
git add frontend/
```

文件必须逐个 `git add -- <file>` 添加。

## 3. Git 终态

| 终态 | 含义 | 允许后续 |
|---|---|---|
| `DONE_CLEAN` | 工作区干净，已 commit + push | 进入下一任务 |
| `DONE_WITH_REGISTERED_DIRTY` | dirty 已分类并登记 | 下一任务 Intake 确认继承 |
| `PARTIAL_DIRTY_REMAINING` | dirty 未收口 | 只能继续当前任务或收口 |
| `BLOCKED_DIRTY_UNKNOWN` | 存在 unknown dirty | 必须先调查 |

## 4. Dirty 十种分类

| 类型 | 允许 stage | 允许 commit |
|---|---|---|
| `current_task` | 是 | 是 |
| `previous_partial` | 仅继承时 | 仅 Batch 中 |
| `docs_state` | 是 | 是（docs 任务） |
| `report_only` | 是 | 是 |
| `frontend` | 是 | 是（frontend 任务） |
| `backend` | 是 | 是（backend 任务） |
| `sql_migration` | 是 | 是（sql 任务） |
| `docker_deploy` | 是 | 是（deploy 任务） |
| `cleanup_retire` | 是 | 是（cleanup 任务） |
| `unknown` | **否** | **否** |

## 5. Allowed Change Set

| Scope | 允许 | 禁止 |
|---|---|---|
| `docs` | harness/, docs/, AGENTS.md | backend/, frontend/, SQL, Docker |
| `backend` | backend/src/, pom.xml | frontend/, harness/, Docker |
| `frontend` | frontend/src/, package.json | backend/, harness/, Docker |
| `docker_deploy` | docker-compose*.yml, Dockerfile | 业务代码, env |
| `cleanup_retire` | harness/reports/, archive/ | 业务代码 |
| `governance` | .github/, CONTRIBUTING.md, SECURITY.md, harness 规则与测试 | 业务代码, SQL, env, 部署 |

`governance` 在 `agent-do.ps1` 中映射为 `Scope=docs`：跳过应用构建、容器重启和数据库迁移，但必须执行安全检查、治理契约测试、Harness 限制检查并生成 evidence。

## 6. 分支与 PR 规范

- 每个任务必须关联 GitHub Issue，并使用独立 worktree。
- Codex 分支命名：`codex/<issue>-<slug>`。
- `main` 是唯一集成主线；`release/real-pre` 是唯一 real-pre 部署来源，不接受未进入 `main` 的任务分支。
- 初次推送后创建 Draft PR；未完成项不得标记 Ready for review。
- PR 必须列出 Owned files、验证结果、evidence、数据库影响、部署需求和回滚方式。
- 合并与部署保持串行；普通任务不拥有 Merge Queue 或发布队列的执行权。

## 7. Commit Message 规范

```text
feat(<scope>): <描述>
fix(<scope>): <描述>
docs(harness): <描述>
chore(cleanup): <描述>
ci(github): <描述>
```

## 8. 关联文件

- [Git Intake Gate](git-change-control.intake.md)
- [Commit / Push / Deploy Gate](git-change-control.commit.md)
- [Git Exit Gate / Unknown Policy / Rollback](git-change-control.exit.md)
- [批次提交流程](git-batch-submit.md)
- [任务后清理](post-task-gc.md)
