# Runbook: Debt Governance

> 防回流机制。新发现的 debt 必须先分类、登记、有关闭条件；不允许"先记在聊天里 / 报告里"。

## 1. Debt Intake（债务入口分类）

新问题不能直接进入 TODO。Agent 必须先问 6 个问题：

| # | 问题 | 决定归类 |
| --- | --- | --- |
| 1 | 是 bug？ | 业务 DEBT（→ `state/p0-p1-register.md`） |
| 2 | 是需求未做？ | 业务 DEBT 或 ADR（→ `docs/决策/ADR-*.md`） |
| 3 | 是测试不足？ | 业务 DEBT（→ `state/p0-p1-register.md`） |
| 4 | 是 harness / docs / 脚本 / 临时文件 / 状态文件债务？ | **Harness DEBT（→ `state/HARNESS_DEBT.md`）** |
| 5 | 是环境漂移 / 端口 / 配置漂移？ | Environment DEBT（→ `state/HARNESS_DEBT.md` 的 Environment 子系统行） |
| 6 | 是历史报告堆积 / 未知 dirty？ | Reports / Dirty DEBT（→ `state/HARNESS_DEBT.md` 的 Reports / State 子系统行） |

## 2. Debt Register（债务登记）

- 业务 DEBT → `state/p0-p1-register.md`（保留现状）。
- Harness DEBT → `state/HARNESS_DEBT.md`（本 runbook 负责）。
- 任何 DEBT 编号不重用，关闭后保留供审计。

每条 DEBT 必含字段：

| 字段 | 说明 |
| --- | --- |
| ID | `DEBT-NNN`（按发现顺序递增） |
| 级别 | P0 / P1 / P2 / P3 |
| 子系统 | Instruction / Tool / State / Feedback / Environment |
| 标题 | 一句话 |
| 现象 | 文件路径 / 报告路径 / 现象描述 |
| 根因 | 简短判断（不深挖） |
| 当前状态 | open / in-progress / fixed / deferred / wontfix |
| 处理任务 | 任务 ID 或"无（本任务处理）" |
| 证据 | 处理后的报告 / 验证命令输出 |
| 关闭时间 | `fixed` 状态时填，格式 `2026-06-03` |

## 3. Debt Exit（关闭条件）

DEBT 关闭必须同时满足：

1. 有修复 commit hash（如果修改了文件）。
2. 有验证命令 + 输出（哪怕 `safety-check docs -DryRun`）。
3. 有 evidence 报告路径。
4. `state/HARNESS_DEBT.md` 的状态字段已更新。
5. `HARNESS_CHANGELOG.md` 写了一行。
6. 若涉及部署：明确"已部署"或"无需部署"。

**不**满足的 DEBT 不得标 `fixed`，只允许 `in-progress` 或 `deferred`。

## 4. Dirty Register（脏文件登记）

任务结束若工作区不干净，**必须**登记到当轮 evidence 报告的 `## Dirty Classification` 段，并按十种分类之一归类：

`current_task / previous_partial / docs_state / report_only / frontend / backend / sql_migration / docker_deploy / cleanup_retire / unknown`

`unknown` dirty **必须**额外生成 `harness/reports/unknown-dirty-investigation-*.md`，并在 SESSION_EXIT_GATE.md 的 Git State Clean 中标 `BLOCKED_DIRTY_UNKNOWN`。

## 5. Report Rotation（报告归档）

`harness/reports/` 累计触发归档的条件：

- 超过 100 份报告未归档；或
- 90 天前的报告无任何被引用；或
- 用户明确要求归档。

归档操作：

```powershell
# 1. 生成候选计划
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan

# 2. 写 manifest（参考 archive/retired-content/20260603-reports-archive/）
# 3. 显式归档
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Archive -Manifest <path>
```

**禁止**：

- 凭自然语言描述直接 `rm` 报告。
- 归档未提交 evidence。
- 归档 `.env*` / 密钥 / migration / 源码 / 部署脚本。

## 6. Prompt Upgrade（提示词 / 文档升级）

每次任务 retro 必须判断：

| 现象 | 升级目标 |
| --- | --- |
| Agent 重复试探命令 | `AGENT_CONTRACT.md` / `TASK_ROUTING.md` |
| 新增任务类型无对应 skill | `skills/<task>.skill.md` |
| 出现新禁止行为 | `FORBIDDEN_SCOPE.md` |
| 出现新 scope 但脚本不支持 | `runbooks/scope-command-matrix.md` + `commands/safety-check.ps1` |
| 状态字段缺失 | `state/HARNESS_DEBT.md` + 现有 state 文件 |
| 验证命令缺一类 | `runbooks/scope-command-matrix.md` + `state/VALIDATION_STATE.md` |

**升级条件**：本次 retro 中至少出现一次该问题。

**升级步骤**：

1. 写新版本到对应 harness 文件。
2. 在 `HARNESS_CHANGELOG.md` 加一行。
3. 在 `state/HARNESS_DEBT.md` 关闭对应 DEBT（如有）。
4. 任务 retro 报告 `## 5. Harness 升级` 段写明升级了哪些文件。

## 7. 与 HARNESS_CHANGELOG / CURRENT_STATE 的关系

- `HARNESS_CHANGELOG.md`：每次 harness 升级**必须**追加一行（不依赖任务类型）。
- `CURRENT_STATE.md`：仅当**业务状态变化**或**关键决策**时追加。
- `state/HARNESS_DEBT.md`：DEBT 状态字段每次处理后必须更新。
- 三者不互替。

## 8. 关联文档

- `harness/state/HARNESS_DEBT.md`
- `harness/feedback/garbage-collection-policy.md`
- `harness/skills/post-task-gc.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/CURRENT_STATE.md`
- `harness/runbooks/closeout-and-gc.md`
- `harness/runbooks/task-lifecycle.md`
