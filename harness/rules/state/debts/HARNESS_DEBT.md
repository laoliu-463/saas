# Harness Debt Register

> 集中登记 harness 自身的债务、清理进度与关闭条件。
> 业务域债务保留在 `state/p0-p1-register.md` 与 `state/KNOWN_ISSUES.md`，本文件**只管 harness 工程债务**。

## 1. 与既有 state 文件的分工

| 文件 | 主责 |
| --- | --- |
| `state/KNOWN_ISSUES.md` | 业务问题卡片，含证据 / 修复状态 |
| `state/p0-p1-register.md` | 业务 P0/P1/P2 风险表 |
| `state/known-risks.md` | 风险分类视图 |
| `state/HARNESS_DEBT.md` (本文件) | **Harness 自身的工程债务**（harness / docs / scripts / 临时产物） |

## 2. 状态口径

- `open`：未开始处理
- `in-progress`：本任务或后续任务正在处理
- `fixed`：有修复 commit + 验证命令 + evidence
- `deferred`：已登记但明确不在本迭代处理
- `wontfix`：明确不处理

## 3. 债务登记（与 `harness-debt-governance-plan-20260603-230334.md` 对齐）

| ID | 级别 | 子系统 | 标题 | 状态 | 处理任务 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| DEBT-001 | P1 | Instruction | 任务生命周期缺显式 runbook | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | runbooks/task-lifecycle.md |
| DEBT-002 | P1 | Instruction | safety-rules.md 与 FORBIDDEN_SCOPE.md 重叠 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | instructions/safety-rules.md 顶部指针 |
| DEBT-003 | P1 | Tool | Scope → Command 决策表分散 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | runbooks/scope-command-matrix.md |
| DEBT-004 | P1 | State | KNOWN_ISSUES / p0-p1-register / known-risks 分工隐式 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | 三文件顶部互引 |
| DEBT-005 | P1 | State | DEPLOYMENT_STATE.md 与 environment/remote-real-pre-env.md 部分重叠 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | DEPLOYMENT_STATE.md 顶部指针 |
| DEBT-006 | P1 | State | TASK_HISTORY.md 自 2026-06-02 后未补 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | TASK_HISTORY.md 追加 |
| DEBT-007 | P1 | Feedback | 缺 docs-only 最小化 evidence/retro 模板 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | feedback/docs-only-template.md |
| DEBT-008 | P1 | Environment | 本地端口 / 健康检查 URL 分散 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | environment/CHEATSHEET.md |
| DEBT-009 | P1 | Tool | VALIDATION_STATE 与 TASK_ROUTING 验证入口表未互引 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | 两处顶部互引 |
| DEBT-010 | P2 | State | CURRENT_STATE.md 缺目录级索引 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | CURRENT_STATE.md 顶部目录 |
| DEBT-011 | P2 | State | QUALITY_LEDGER 与 DOMAIN_STATUS 重复 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | QUALITY_LEDGER 顶部指针 |
| DEBT-012 | P2 | Instruction | doc/ + harness/ + README.md 三方并存 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | harness/README.md 顶部声明 |
| DEBT-013 | P2 | Environment | 12 个 ad-hoc log 未 .gitignore 排除 | fixed | HARNESS-DEBT-GC-001 | harness/reports/harness-debt-gc-001-inventory-20260604-001052.md |
| DEBT-014 | P2 | Reports | reports/ 72 份未触发归档 | wontfix | HARNESS-DEBT-GC-001 | harness/reports/harness-debt-gc-001-inventory-20260604-001052.md |
| DEBT-015 | P2 | doc | doc/01-instructions/05 与 instructions/* 重叠 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | doc 顶部加指针 |
| DEBT-016 | P2 | Plan | DDD_DOMAIN_TASK_MATRIX 未更新 | deferred | DDD 任务 | — |
| DEBT-017 | P2 | Feedback | garbage-collection-policy 未列 reports/ 为受保护 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | GC 政策顶部声明 |
| DEBT-018 | P2 | Quality | QUALITY_LEDGER 中 Harness 等级仍为 B | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | QUALITY_LEDGER 升级 A- |
| DEBT-019 | P2 | Environment | 缺环境速查表 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | environment/CHEATSHEET.md |
| DEBT-020 | P3 | Tool | agent-do.ps1 缺 -Scope harness | deferred | HARNESS-AGENT-DO-HARDEN | — |
| DEBT-021 | P3 | Feedback | 缺"未变更文件"自动化检测 | deferred | 后续 | — |
| DEBT-022 | P3 | Tool | safety-check scope 列表不全 | deferred | HARNESS-AGENT-DO-HARDEN | — |
| DEBT-023 | P3 | State | 缺 Harness 内部债务登记文件 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | state/HARNESS_DEBT.md（本文件） |
| DEBT-024 | P3 | Environment | .env.real-pre.example 引用不统一 | deferred | 后续 | — |
| DEBT-025 | P3 | Plans | 缺 harness 自身迭代路线图 | in-progress | HARNESS-DEBT-GOVERNANCE-ITERATION | plans/HARNESS_ITERATION_ROADMAP.md |
| DEBT-026 | P0 | Git/Tool | `agent-do` 无条件调用全工作区自动暂存/推送，与单任务逐文件 Gate 和 gitee 只读口径冲突 | open | HARNESS-AGENT-DO-SAFE-GIT | `harness/reports/git-intake-20260710-125023.md` |
| DEBT-027 | P1 | Reports/Tool | `retire-content Archive` 将超 200 行报告原样移入 archive，导致 limits 继续失败 | open | HARNESS-RETIRE-PACK-001 | `harness/reports/content-retire-20260710-125326.md` |

## 4. 关闭条件

每条 DEBT 关闭必须同时满足：

1. 有修复 commit hash（如果修改了文件）。
2. 有验证命令 + 输出（哪怕是 `safety-check docs -DryRun`）。
3. 有 evidence 报告路径。
4. 状态字段已更新到本表 + `HARNESS_CHANGELOG.md`。
5. 若涉及部署：明确"已部署"或"无需部署"。

## 5. 写入规则

- 新发现债务必须立刻追加，**不得只在聊天记录 / 报告里提及**。
- 一条 DEBT 只记录一种债务，复合问题拆条。
- DEBT 关闭必须有 evidence；不允许"已修"等含糊字。
- DEBT 编号不重用，关闭后保留在表中供审计。
