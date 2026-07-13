# Harness 分层文件门禁 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 ADR-013，使 Harness 能阻断新增文件债务、停止生成根目录时间戳报告，并且只提交当前任务拥有的文件。

**Architecture:** 用一个纯 PowerShell 模块采集当前/基线快照并输出任务门禁与仓库健康度；命令脚本复用稳定报告路径和显式文件集合。历史根报告按 manifest 分为 evidence、retro、content-retire 三组归档，避免单目录超过 50。

**Tech Stack:** Windows PowerShell 5.1、Pester 3.4、Git、现有 Harness scripts。

---

## 文件结构

- Create: `harness/scripts/modules/HarnessFileGovernance.psm1` — 快照、比较、状态模型。
- Create: `harness/scripts/tests/check-harness-limits.Tests.ps1` — 50/50/200、基线和状态测试。
- Create: `harness/scripts/tests/report-lifecycle.Tests.ps1` — 稳定路径和显式 owned files 测试。
- Create: `harness/scripts/tests/retire-content.Tests.ps1` — 分组归档测试。
- Modify: `harness/scripts/check-harness-limits.ps1` — CLI、报告和退出码。
- Modify: `harness/scripts/commands/{_lib,agent-do,collect-evidence,new-retro,retire-content,git-push-safe}.ps1` — 生命周期与提交顺序。
- Create: `harness/manifests/reports-root-retirement-20260713.json` — 75 个已跟踪时间戳报告清单。
- Modify: `AGENTS.md`、`harness/README.md`、`harness/INDEX.md`、`harness/scripts/README.md` 和相关 canonical rules/templates — 新入口和状态口径。

### Task 1: 用测试锁定基线感知门禁

- [ ] **Step 1: 写失败测试**

在 `check-harness-limits.Tests.ps1` 创建临时 Git 仓库，复用以下调用约定：

```powershell
$result = & powershell -NoProfile -File $checker -RepoRoot $repo `
  -BaselineRef HEAD -OwnedFiles $owned -NoReport 2>&1
$resultText = $result -join "`n"
$LASTEXITCODE | Should Be $expectedExit
$resultText | Should Match $expectedStatus
```

覆盖：`89→89` 为 task PASS/repository PARTIAL、`89→90` FAIL、`89→88` task PASS、40/50 文件预警/上限、160/200 行预警/上限、新根时间戳报告 FAIL、脚本超过 200 行 PASS、额外一级目录 FAIL。

- [ ] **Step 2: 运行红测**

Run: `powershell -NoProfile -Command "Invoke-Pester -Script harness/scripts/tests/check-harness-limits.Tests.ps1 -EnableExit"`

Expected: FAIL，原因是 checker 尚无 `RepoRoot/BaselineRef/OwnedFiles/NoReport` 参数和分层状态。

- [ ] **Step 3: 实现最小检查模块和 CLI**

模块导出以下稳定接口：

```powershell
Export-ModuleMember -Function @(
  'Get-HarnessFileSnapshot',
  'Get-HarnessBaselineSnapshot',
  'Compare-HarnessFileGovernance',
  'Format-HarnessGovernanceReport'
)
```

结果对象固定包含 `TaskGate`、`RepositoryHealth`、`Violations`、`Warnings`、`HistoricalDebt`。任务新增/恶化硬违规退出 1；仅历史债务退出 0，并将报告稳定写入 `harness/reports/current/latest-harness-limits-check.md`。

- [ ] **Step 4: 运行绿测和语法检查**

Run: `powershell -NoProfile -Command "Invoke-Pester -Script harness/scripts/tests/check-harness-limits.Tests.ps1 -EnableExit"`

Expected: 全部 PASS。随后用 PowerShell Parser 检查模块、checker，Expected: 0 parse errors。

- [ ] **Step 5: 提交**

```powershell
git add -- harness/scripts/modules/HarnessFileGovernance.psm1 harness/scripts/check-harness-limits.ps1 harness/scripts/tests/check-harness-limits.Tests.ps1
git commit -m "feat(harness): enforce baseline-aware file budgets"
```

### Task 2: 稳定报告路径和显式文件所有权

- [ ] **Step 1: 写失败测试**

`report-lifecycle.Tests.ps1` 必须验证：

```powershell
(New-HarnessReportPath -RepoRoot $repo -ReportKey 'file-governance') |
  Should Be (Join-Path $repo 'harness\reports\current\latest-file-governance.md')
$dryRun = & powershell -NoProfile -File $gitPush -Message 'test: owned files' `
  -OwnedFiles @('owned.md') -DryRun 2>&1
($dryRun -join "`n") | Should Match 'owned.md'
($dryRun -join "`n") | Should Not Match 'unrelated.md'
```

另测空 `OwnedFiles` 在有变更时失败、预先暂存非 owned 文件时失败、report key 路径穿越被拒绝。

- [ ] **Step 2: 运行红测**

Run: `powershell -NoProfile -Command "Invoke-Pester -Script harness/scripts/tests/report-lifecycle.Tests.ps1 -EnableExit"`

Expected: FAIL，原因是当前 report path 使用时间戳且 git-push-safe 接管全工作区。

- [ ] **Step 3: 实现稳定路径和 scoped staging**

`_lib.ps1` 新增 `ConvertTo-HarnessReportKey`、`Resolve-HarnessOwnedFiles`、稳定 `New-HarnessReportPath`。`git-push-safe.ps1` 接收 `[string[]]$OwnedFiles`，只扫描、暂存、校验并提交这些路径；推送当前 upstream，不再强制写只读镜像。

`collect-evidence.ps1` 接收 `ReportKey/OwnedFiles/RetroSummary`，只记录 owned files 并返回报告路径。`agent-do.ps1` 在非 dry-run 且有变更时要求 `OwnedFiles`，把 evidence 自动加入 owned set，在提交前合并 retro；删除结尾无条件 `new-retro`。远端部署后若更新 evidence，执行单独的 evidence-only scoped commit。

- [ ] **Step 4: 运行绿测和 agent-do dry-run**

Run: Pester report lifecycle；随后执行 `agent-do.ps1 -Scope docs -ReportKey harness-file-governance -OwnedFiles docs/方案/PLAN-005-Harness分层文件门禁实施.md -ContentMaintenance off -DryRun`。

Expected: 测试 PASS；dry-run 只列 owned file 和稳定 current report。

- [ ] **Step 5: 提交**

```powershell
git add -- harness/scripts/commands/_lib.ps1 harness/scripts/commands/agent-do.ps1 harness/scripts/commands/collect-evidence.ps1 harness/scripts/commands/new-retro.ps1 harness/scripts/commands/git-push-safe.ps1 harness/scripts/tests/report-lifecycle.Tests.ps1
git commit -m "feat(harness): scope reports and git staging"
```

### Task 3: 分组归档历史根报告

- [ ] **Step 1: 写失败测试**

在临时仓库 manifest 中定义 `archiveGroup`，验证：

```json
{"path":"harness/reports/evidence-20260713-000000.md","action":"archive","archiveGroup":"evidence"}
```

dry-run 输出必须包含 `.../<batch>/evidence/evidence-20260713-000000.md`；`../escape`、绝对路径和空 group 必须失败。

- [ ] **Step 2: 红测、最小实现、绿测**

Run: `powershell -NoProfile -Command "Invoke-Pester -Script harness/scripts/tests/retire-content.Tests.ps1 -EnableExit"`

Expected: 先 FAIL；实现 `archiveGroup` 边界校验和分组目标后全部 PASS。retire 报告改写稳定路径 `reports/current/latest-content-retire.md`。

- [ ] **Step 3: 创建 manifest 并先 dry-run**

manifest 只包含 `git ls-tree HEAD` 中 `harness/reports/` 根的 75 个 `evidence-*`、`retro-*`、`content-retire-*`；分别使用三个 group。执行 Archive dry-run，Expected: 75 条操作、0 个缺失、每组不超过 50。

- [ ] **Step 4: 执行归档并复查**

ArchiveRoot: `harness/archive/by-date/2026-07-13/harness-report-root`。执行后 `reports/` 根直接文件目标为 12；三个归档组分别不超过 30；所有 manifest 源路径均不存在、目标均存在。

- [ ] **Step 5: 提交**

显式暂存 retire 脚本、测试、manifest、移动文件和稳定 content-retire 报告，提交 `chore(harness): archive timestamped root reports`。

### Task 4: 对齐规则与入口

- [ ] **Step 1: 更新 canonical 文档**

把 40/50、160/200、reports root 20、baseline 语义、current 稳定报告、owned files 写入结构/保留/报告政策；将 active 文档中的 `evidence-*`、`retro-*`、`content-retire-*` 和旧 agent-do 示例改为新入口。历史状态中的旧证据路径不改写。

- [ ] **Step 2: 更新状态**

`HARNESS_DEBT.md` 将 DEBT-026、DEBT-027 标记为 fixed，并登记 commit/evidence；`harness/rules/changelog.md` 记录实现。压缩接近 200 行的 active 文档，保证 Harness 非脚本文本不超过 200 行。

- [ ] **Step 3: 验证并提交**

Run: `rg` 确认 active 入口无旧时间戳生成指令；运行全部 Pester、PowerShell Parser、`check-harness-limits.ps1 -BaselineRef HEAD`。

Expected: task gate PASS；repository health PASS 或如实 PARTIAL，且没有新增硬违规。提交 `docs(harness): align layered file governance rules`。

### Task 5: 完整 Harness 验证和交付

- [ ] **Step 1: 运行 Gate 0**

执行 `agent-do.ps1 -Env real-pre -Scope docs -ReportKey harness-file-governance -OwnedFiles <本任务显式文件列表> -ContentMaintenance off -Message "feat(harness): implement layered file governance"`。

Expected: safety、Pester、结构检查 PASS；build/restart/health/business 按 `Scope=docs` 跳过；生成一份 current evidence，retro 内联。

- [ ] **Step 2: 最终证据检查**

确认 evidence 包含环境、分支、最终 subject commit、任务/仓库双状态、未执行项和剩余风险；code-review-graph 对 PowerShell 无调用图时明确记录手工引用扫描结果。

- [ ] **Step 3: 提交、推送与工作区核对**

仅暂存 final evidence/state，提交 `docs(harness): record layered governance evidence`，推送 `origin codex/harness-file-governance`。Expected: 隔离 worktree clean；主工作区原有并发 dirty 不变。
