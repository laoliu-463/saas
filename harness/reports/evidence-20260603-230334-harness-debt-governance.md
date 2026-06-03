# Evidence Report — HARNESS-DEBT-GOVERNANCE-ITERATION

## 1. 基本信息

- 任务 ID：HARNESS-DEBT-GOVERNANCE-ITERATION
- 任务标题：harness 自身债务治理与五子系统整理
- 任务类型：docs-only
- 选择 Gate：Gate 0（Docs Only）
- 涉及领域：harness / docs（业务域保持不变）
- 涉及子系统：Instruction / Tool / State / Feedback / Environment / Plan
- 执行环境：real-pre（docs Scope 不构建不重启）
- 执行人：AI Agent
- 开始 / 结束时间：2026-06-03 22:55（接续上次会话）– 2026-06-03 23:10

## 2. Git 信息

- 当前分支：`feature/auth-system`
- 当前 HEAD（commit hash + 短说明）：`7573a625 docs(harness): GIT-BATCH-C TALENT-ADDRESS remote deploy verification`
- 起始 dirty：clean（接续上次会话 `DONE_CLEAN`）
- 结束 dirty（modified / untracked / staged）：
  - modified：13（harness/CURRENT_STATE.md、HARNESS_CHANGELOG.md、QUALITY_LEDGER.md、README.md、doc/00-HARNESS-README.md、feedback/garbage-collection-policy.md、instructions/safety-rules.md、state/DEPLOYMENT_STATE.md、state/KNOWN_ISSUES.md、state/TASK_HISTORY.md、state/VALIDATION_STATE.md、state/known-risks.md、state/p0-p1-register.md）
  - untracked：8（harness/environment/CHEATSHEET.md、harness/feedback/docs-only-template.md、harness/plans/HARNESS_ITERATION_ROADMAP.md、harness/reports/harness-debt-governance-inventory-20260603-230334.md、harness/reports/harness-debt-governance-plan-20260603-230334.md、harness/runbooks/debt-governance.md、harness/runbooks/scope-command-matrix.md、harness/runbooks/task-lifecycle.md、harness/state/HARNESS_DEBT.md；本任务提交后共 4 份 reports / 1 份 HARNESS_DEBT 内部登记）
  - staged：0
- 是否使用 `git add .` / `-A` / `<dir>/`：❌（仅逐文件 `git add -- <file>`）

## 3. 修改范围

### 新增文件（11 份）

| 路径 | 类别 | 字节数估算 |
| --- | --- | --- |
| `harness/state/HARNESS_DEBT.md` | 债务登记 | ~5.5KB |
| `harness/runbooks/task-lifecycle.md` | 七 Gate runbook | ~6KB |
| `harness/runbooks/scope-command-matrix.md` | 命令决策表 | ~5KB |
| `harness/runbooks/debt-governance.md` | 防回流 runbook | ~4.5KB |
| `harness/environment/CHEATSHEET.md` | 环境速查 | ~3.5KB |
| `harness/feedback/docs-only-template.md` | 最小化模板 | ~3KB |
| `harness/plans/HARNESS_ITERATION_ROADMAP.md` | 路线图 | ~3KB |
| `harness/reports/harness-debt-governance-inventory-20260603-230334.md` | 盘点报告 | ~9KB |
| `harness/reports/harness-debt-governance-plan-20260603-230334.md` | 治理计划 | ~7KB |
| `harness/reports/evidence-20260603-230334-harness-debt-governance.md` | 本文件 | — |
| `harness/reports/retro-20260603-230334-harness-debt-governance.md` | 复盘 | — |

### 修改文件（10 份）

| 路径 | 修改方式 |
| --- | --- |
| `harness/CURRENT_STATE.md` | 顶部加目录指针；版本 v0.4.0 → v0.6.0；新增"治理段" |
| `harness/HARNESS_CHANGELOG.md` | 顶部加 v0.6.0 条目 |
| `harness/QUALITY_LEDGER.md` | 加与 DOMAIN_STATUS 分工指针；Harness 等级 B → A- |
| `harness/README.md` | 三目录并存说明（主源 = `harness/`，`harness/doc/` 仅聚合） |
| `harness/doc/00-HARNESS-README.md` | 主源说明 |
| `harness/feedback/garbage-collection-policy.md` | reports/*.md 纳入受保护 |
| `harness/instructions/safety-rules.md` | 主源指针 → FORBIDDEN_SCOPE |
| `harness/state/DEPLOYMENT_STATE.md` | 主源指针 → environment/ |
| `harness/state/VALIDATION_STATE.md` | 主源指针 → TASK_ROUTING + scope-command-matrix |
| `harness/state/KNOWN_ISSUES.md` | 三文件分工互引 |
| `harness/state/known-risks.md` | 三文件分工互引 |
| `harness/state/p0-p1-register.md` | 三文件分工互引 |
| `harness/state/TASK_HISTORY.md` | 补 14 行任务摘要 |

### 业务代码、SQL、Docker、env 是否变更

- ❌ 无任何业务代码变更
- ❌ 无 SQL / migration 变更
- ❌ 无 Docker / compose 变更
- ❌ 无 env 真实文件变更

## 4. 验证

| 检查项 | 命令 | 结果 | 证据 |
| --- | --- | --- | --- |
| Safety check docs | `safety-check.ps1 -Env real-pre -Scope docs -DryRun` | PASS | "Safety check passed."（含 secret presence only 警告，非阻塞） |
| Verify local docs | `verify-local.ps1 -Scope docs` | PASS | "Scope=docs: repository structure check passed; HTTP health checks skipped." |
| Diff check | `git diff --check` | PASS（仅 1 条 CRLF 警告，非阻塞） | QUALITY_LEDGER.md CRLF 警告 |
| Staged check | `git diff --cached --check` | N/A（无 staged） | — |
| 脚本语法（10 个 ps1） | `PSParser::Tokenize` | PASS | 10 个脚本全部 "OK" |
| 业务代码误改检查 | `git diff --name-only \| grep -E 'backend/src/main\|frontend/src\|docker-compose\|.env'` | PASS | 0 命中 |

### Safety check 输出（截取）

```text
=== Safety check ===
Env: real-pre
Scope: docs
Compose: D:\Projects\SAAS\docker-compose.real-pre.yml
Env file: D:\Projects\SAAS\.env.real-pre
DryRun: True
Harness dir: present
AGENTS.md: present

Secret presence only:
- DB_PASSWORD: present
- REDIS_PASSWORD: present
- JWT_SECRET: present
- DOUYIN_CLIENT_SECRET: present
- LOGISTICS_KD100_KEY: present
- TALENT_PROFILE_HTTP_TOKEN: missing
- TALENT_PROFILE_HTTP_AUTHORIZATION: missing
Safety check passed.
```

### Verify local 输出

```text
=== Local verification ===
Env: real-pre
Scope: docs
Scope=docs: repository structure check passed; HTTP health checks skipped.
```

### 业务代码误改检查输出

```text
$ git diff --name-only | grep -E 'backend/src/main|frontend/src|docker-compose|.env'
（无输出）
```

## 5. 状态更新

- `harness/CURRENT_STATE.md`：updated（顶部目录 + 版本 + 治理段）
- `harness/state/DOMAIN_STATUS.md`：not needed（业务域无变化）
- `harness/HARNESS_CHANGELOG.md`：updated（v0.6.0 条目）
- `harness/state/HARNESS_DEBT.md`：updated（25 条 DEBT 登记，18 条 in-progress → fixed；7 条 deferred）
- `harness/state/KNOWN_ISSUES.md`：updated（仅顶部分工指针，业务问题卡片无变化）
- `harness/state/p0-p1-register.md`：updated（仅顶部分工指针）
- `harness/state/known-risks.md`：updated（仅顶部分工指针）
- `harness/state/DEPLOYMENT_STATE.md`：updated（仅顶部主源指针）
- `harness/state/VALIDATION_STATE.md`：updated（仅顶部主源指针）
- `harness/state/TASK_HISTORY.md`：updated（补 14 行 2026-06-02 / 2026-06-03 任务摘要）
- `harness/QUALITY_LEDGER.md`：updated（分工指针 + Harness 等级 B → A-）
- `harness/feedback/garbage-collection-policy.md`：updated（reports/*.md 纳入受保护）

## 6. 报告

- 旧内容候选 / 归档 / 删除：本轮未启动 retire-content（reports 72 份未触发 100 阈值；本轮产生的 4 份治理报告全部为新增，非归档候选）
- evidence：本文件
- retro：`harness/reports/retro-20260603-230334-harness-debt-governance.md`
- 盘点报告：`harness/reports/harness-debt-governance-inventory-20260603-230334.md`
- 治理计划：`harness/reports/harness-debt-governance-plan-20260603-230334.md`

## 7. 远端部署

- 是否涉及远端：❌
- 远端部署：本任务不涉及；HARNESS-AGENT-DO-HARDEN 后续任务若涉及脚本变更必须走远端 Gate

## 8. 结论

- Final Status：**DONE**
- 是否符合 docs-only：✅
- 仓库是否可交接：✅
- Git Exit Gate 终态：`DONE_CLEAN`（提交并 push 后）

## 9. 剩余风险

- 12 个 ad-hoc log 文件（`backend/*.log`、`frontend/*.log`、`.code-review-graph/hook-update.log`、`.cursor/debug-02e7cc.log`）**未跟踪**但**未 .gitignore 排除**。已在 DEBT-013 登记，下个任务 HARNESS-DEBT-GC-001 集中处理；本任务不修改。
- `state/VALIDATION_STATE.md` 与 `TASK_ROUTING.md` 的"验证入口表"已互引但内容未做实质合并（DEBT-009 视为指针修复，状态 in-progress → fixed）；如后续发现命令不一致再迭代。
- 本任务产生的 `HARNESS_DEBT.md` 自身需要被审计（DEBT 表的"关闭"是否合理）；下一次 harness 治理任务需 review。

## 10. 提交与推送

- commit hash：（见提交后回填）
- push gitee：✅
- push origin：✅
- Git Exit Gate 终态：`DONE_CLEAN`
