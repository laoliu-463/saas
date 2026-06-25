# Git Intake Gate

> 主文件：[git-change-control.md](git-change-control.md)

## 触发

每个任务开始前（包括 docs-only）必须执行。

## 命令

```powershell
git status --short
git diff --name-only
git log -1 --oneline
git branch --show-current
```

## 必须输出

```text
## Git Intake
- branch: feature/xxx
- head: <hash> <msg>
- dirty_total: N
- modified: N
- deleted: N
- untracked: N
- staged: empty / <list>
- unknown_files: <list>
- decision: START / DELAY / BLOCKED
- reason: ...
```

## 工作区不干净时的处理

| dirty 状态 | 处理 |
|---|---|
| 与当前任务无关但归属明确 | 先提交前置任务或走 PARTIAL 继承 |
| 来源不明（unknown） | 先执行调查，不得开始任务 |
| 仅状态文件 / 报告 | 归入 docs_state / report_only |
| 环境敏感文件 | 立即停止，按 FORBIDDEN_SCOPE.md 处理 |

不允许"先改了再说"。必须先 Intake Gate，再做修改。
