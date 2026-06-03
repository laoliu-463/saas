# Post Task GC

> 本文件定义任务结束后的清理流程（garbage collection），包含临时文件清理、报告提交、状态文件检查、dirty 归属登记和未提交项进入下一任务队列。

## 1. 触发时机

- 任务执行完成并通过 Completion Gate 之后。
- 任务标记为 `PARTIAL` 之后。
- 任务标记为 `BLOCKED` 之后。
- Session Exit Gate 之前必须先执行。

## 2. 清理目标

1. 删除临时 debug 文件。
2. 提交或归档已生成的报告。
3. 同步状态文件（`CURRENT_STATE.md`、`DOMAIN_STATUS.md`、`HARNESS_CHANGELOG.md`）。
4. 登记 dirty 归属。
5. 未提交项进入下一任务队列。

## 3. 临时文件清理

### 3.1 必须删除

- 临时 SQL 脚本（`tmp-*.sql`、`debug-*.sql`）。
- 临时 Python / PowerShell 脚本（`debug-*.py`、`tmp-*.ps1`）。
- 临时日志（`*.log` 不在 `runtime/qa/` 下）。
- 临时 Markdown（`tmp-*.md`、`scratch-*.md`、`draft-*.md`，除被明确归入报告）。
- `*.tmp` / `*.bak` / `*.orig` / `*.rej`。
- 调试产生的 `nul` / `debug-*` / `test-output-*`。
- IDEA / VSCode 临时配置（`.idea/workspace.xml`、`*.swp`、`*.swo`）。
- `node_modules/`、`backend/target/`、`frontend/dist/`、`frontend/node_modules/`（如被错误跟踪）。

### 3.2 推荐保留

- 正式报告（`harness/reports/`）。
- QA 输出（`runtime/qa/out/`）。
- Evidence / Retro。
- 计划文档。
- 可复用脚本。

### 3.3 临时文件处理命令

```powershell
# 列出可疑临时文件
Get-ChildItem -Recurse -Include *.tmp,*.bak,*.orig,*.rej,nul,debug-*,test-output-* -Force 2>$null

# 检查 node_modules / target / dist 是否被误跟踪
git ls-files | Select-String -Pattern "node_modules/|/target/|/dist/"

# 删除本地临时文件
Remove-Item -Path "<file>" -Force -ErrorAction SilentlyContinue
```

## 4. 报告提交

### 4.1 报告文件清单

- 任务主报告：`harness/reports/<task-id>-<timestamp>.md`。
- Evidence 报告：`harness/reports/evidence-<timestamp>.md`。
- Retro 报告：`harness/reports/retro-<timestamp>.md`。
- 状态收口报告：`harness/reports/git-batch-<N>-<scope>-<timestamp>.md`。

### 4.2 报告必须完成

- 时间、环境、分支、commit hash。
- 修改文件清单。
- 验证结果（Build / Health / API Smoke / UI Smoke / Business Flow）。
- 结论（DONE / PARTIAL / BLOCKED / FAILED / ROLLBACK_REQUIRED）。
- 剩余风险。
- 下一步建议。

### 4.3 报告提交原则

- 报告文件本身**不**与业务代码 commit 混在一起。
- 报告通常作为独立 Batch（如 `GIT-BATCH-N` reports 批次）提交。
- 报告被 commit 之前必须确认已经过 `git diff --cached --check`。

## 5. 状态文件检查

### 5.1 必须更新的文件

| 文件 | 何时更新 |
| --- | --- |
| `harness/CURRENT_STATE.md` | 每次任务完成 / Session Exit |
| `harness/state/DOMAIN_STATUS.md` | 每次任务完成（含 Session Exit 时的领域状态） |
| `harness/state/KNOWN_ISSUES.md` | 新问题登记 / 修复记录 |
| `harness/state/DECISIONS.md` | 新决策索引 |
| `harness/HARNESS_CHANGELOG.md` | Harness 版本变更或新决策记录 |
| `harness/QUALITY_LEDGER.md` | 模块质量变化 |

### 5.2 状态文件不能与业务代码 commit 混在一起

- 状态文件改动可与 harness 规则改动作为同一 docs / harness batch 提交。
- 但业务代码 commit（feature / fix / refactor）不得包含状态文件，除非该状态文件只为该业务任务服务且范围清晰。

## 6. Dirty 归属登记

### 6.1 登记原则

任务结束后，所有 dirty 文件必须归入以下状态之一：

- `current_task`：当前任务的修改（已 commit + push 即消失）。
- `previous_partial`：前置 PARTIAL 任务的遗留（已登记到 Batch 计划）。
- `docs_state` / `report_only` / `cleanup_retire`：可在 docs / reports / cleanup batch 中提交。
- `unknown`：必须先调查，不能 commit。

### 6.2 登记表

每个任务结束时必须在报告中记录：

```text
## Dirty 归属登记
| 文件 | 状态 | 分类 | 下一动作 |
| --- | --- | --- | --- |
| <path> | M / D / A / ?? | <10 种之一> | commit / archive / delete / investigate |
```

### 6.3 禁止

- 不允许 dirty 留在工作区而无归属。
- 不允许 dirty 既未 commit 也未登记到 Batch 计划。
- 不允许 unknown dirty 进入任何 batch。

## 7. 未提交项进入下一任务队列

### 7.1 队列位置

下一任务队列的入口：

- `harness/reports/sync-plan-*.md`（如已有）。
- `harness/state/KNOWN_ISSUES.md`。
- `harness/state/DECISIONS.md`（如为决策类）。
- 任务主报告（任务内 Batch 计划）。

### 7.2 必须记录

- 任务编号（如 `GIT-BATCH-4`、`GIT-CLEANUP-001`）。
- 文件清单。
- 风险说明。
- 推荐执行顺序。

### 7.3 禁止

- 不允许 dirty 留在工作区而不在任何任务队列中。
- 不允许仅口头说明"下次再处理"。

## 8. Git 清理流程

任务结束后必须执行：

```powershell
# 1. Git Intake Gate
git status --short
git diff --name-only
git log -1 --oneline
git branch --show-current
git remote -v

# 2. 临时文件检查
Get-ChildItem -Recurse -Include *.tmp,*.bak,nul -Force

# 3. 大文件检查
git ls-files | Where-Object { (Get-Item $_ -ErrorAction SilentlyContinue).Length -gt 1MB }

# 4. 状态文件 diff 检查
git diff --stat -- harness/

# 5. 报告文件 untracked 检查
git ls-files --others --exclude-standard harness/reports/
```

## 9. 与其他文件的关系

- `harness/skills/git-change-control.md`：必须遵守全部 Git Gate。
- `harness/skills/git-batch-submit.md`：批次提交流程。
- `harness/SESSION_EXIT_GATE.md`：会话退出前必须先完成本流程。
- `harness/AGENT_CONTRACT.md`：必须遵守总规则。

## 10. 禁止事项

1. 禁止留下临时文件无归档。
2. 禁止留下 dirty 无归属。
3. 禁止把 PARTIAL 状态写成 DONE。
4. 禁止留下未更新的状态文件。
5. 禁止把 unknown dirty 与其他任务一起提交。
6. 禁止"先 commit 再说"或"下次再清理"。

## 11. 完成判定

post-task-gc 完成必须满足：

- 无本地临时文件。
- 无 unknown dirty。
- 所有 dirty 已分类并登记。
- 状态文件已更新。
- 已生成报告（如适用）。
- 下一任务队列已更新。
