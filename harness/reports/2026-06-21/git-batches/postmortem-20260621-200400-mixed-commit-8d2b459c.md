# Postmortem: 事故 commit 8d2b459c

## 时间
2026-06-21 20:26:21 Asia/Shanghai

## 触发
在执行 `git restore --staged` 撤销之前误 `git add` 的 colonel 试验田 + harness reports 期间，harness 的 `agent-do.ps1 -Scope docs` 自动运行（详见 `harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-1957-2040/evidence-20260621-195731.md` + `harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-1957-2040/retro-20260621-200043.md`），自动 `git-push-safe.ps1 -Message "docs: update harness folder limits to 50"` 把 51 个文件 commit。

## commit 8d2b459c 内容（51 文件，混合 4 主题）
- harness 治理规则 6 文件（AGENTS.md + harness/README + harness/rules/* 6 docs）— PASS
- harness 脚本产物 5 文件（harness-gc-plan + content-retire + evidence + latest-harness-limits-check + check-harness-limits.ps1）— harness 自动化产物
- harness 报告 34 文件（2026-06-21 全量 retro/evidence）— PASS（昨日 DataScope/Facade/Permission 三批切片）
- colonel 试验田 6 文件（ContactUpdateApplicationService + Router + Repository + LegacyColonelPartnerRepositoryAdapter + 2 test）— **⚠️ 无 PASS evidence，未审**

## 违反的约束
- 小切片闭环原则（单 commit 混合 4 主题）
- 严禁改变生产环境行为直至完成（试验田无 evidence）
- 干净提交边界（应拆 4-6 个独立 commit）

## 已有的可恢复路径
- `git reset --soft HEAD~1` 退到 `a1c4f19c`，8d2b459c 的 51 文件变为 staged，可重新拆 commit
- 当前 `stash@{0}` 仍存有 16 个原始悬疑文件（与 8d2b459c 中 colonel 试验田有重叠，stash 内版本早于 8d2b459c）

## 当前决策
保留 8d2b459c 不 reset（避免 working tree 与 stash 内容冲突），本 postmortem 明确分组责任，PR review 时合并讨论。

## 未提交变更（working tree）
- `BeanPropertyCopy.java`（1 个工具类 untracked，证据未到位）
- 无 staged，无 unstaged M

## 后续防护
- 调查 Hermes/Claude Code background hook 是否触发 agent-do.ps1
- 考虑 stash pop 前先 mvn test 验证试验田（BehaviorParityTest 已有但未跑过）
- PR review 节点统一处理 8d2b459c 分组
