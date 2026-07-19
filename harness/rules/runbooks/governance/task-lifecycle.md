# Runbook: Task Lifecycle

> 任何任务（docs-only / backend / frontend / full / deploy / diagnosis）必须按本 runbook 走完七个 Gate。
> 本 runbook 是 `harness/rules/policies/agent-contract.md`、`harness/rules/governance/COMPLETION_GATES.md` 和 `harness/rules/governance/session-exit-gate.md` 的执行流程视图。

## 0. 三件套关系

- **AGENT_CONTRACT.md**：定义 Agent 必须遵守的协议（"做什么"）。
- **COMPLETION_GATES.md**：定义任务完成的硬门禁（"通过什么才算完成"）。
- **SESSION_EXIT_GATE.md**：定义会话结束的清洁度检查（"仓库是否可交接"）。
- **本 runbook**：定义七 Gate 顺序（"先后怎么走"）。

---

## Gate 1 — Intake Gate

**目的**：理解任务、判断类型、判断领域、判断禁止范围、判断是否需要容器 / 远端。

**必做动作**：

1. 读取 `CLAUDE.md` + `docs/README.md`。
2. 读取 `harness/rules/state/snapshots/01-当前项目状态.md`。
3. 读取 `harness/rules/governance/task-routing.md` 找到对应任务类型必读文件。
4. 执行 `git status --short` / `git log -1` / `git branch --show-current` / `git remote -v`，并记录到本轮 Intake 段落。
5. 判断任务类型：`docs | backend | frontend | full | deploy | diagnosis`。
6. 判断涉及领域（参见 `harness/DOMAIN_MAP.md`）。
7. 阅读 `harness/rules/governance/forbidden-scope.md`，明确本任务**禁止**做哪些事。
8. 判断是否需要：
   - 重启本地容器？
   - 远端部署？
   - 真实样本 / Token / 权限包？

**输出**：本轮 Intake 段落（可附在 evidence 报告 `## 1. 基本信息`）。

**禁止**：未做 Intake 直接进入 Gate 2。

---

## Gate 2 — Scope Gate

**目的**：把"允许修改 / 禁止修改 / 可能误改"三类文件显式列出。

**必做动作**：

1. 列出本轮 Allowed Change Set（具体到路径或目录）。
2. 列出 Forbidden Change Set（必须显式说明：env、密钥、`.env.real-pre`、`*.key`、未授权的 `backend/` `frontend/` `docs/`）。
3. 列出可能被误改的文件（如多份报告同时修改某一处文字）。
4. 明确：`git add .` / `git add -A` / `git add <dir>/` **禁止**。
5. 把当前 `git status --short` 中的 dirty 归入十种分类之一（参见 `harness/rules/skills/git/git-change-control.md` 第 3 节）。

**输出**：Allowed / Forbidden / Risky 三段；dirty 分类表。

**禁止**：未限定 scope 即开始修改文件。

---

## Gate 3 — Implementation Gate

**目的**：小步、可回滚、便于审核。

**必做动作**：

1. 一个任务只解决一个主责问题。
2. 业务代码和 harness 文档分批提交（不要混 commit）。
3. 不在实现中混写未在本任务范围的"顺手优化"。
4. 任何 dirty 必须有归属：当前任务 / 前置任务 / 状态 / 报告 / 清理。
5. 不允许 `git add` 未在本轮 Allowed Set 内的文件。

**输出**：本轮 diff（`git diff --stat`）。

**禁止**：多任务 commit / 把无关报告塞进本轮 commit。

---

## Gate 4 — Verification Gate

**目的**：按 scope 跑对应最小验证。

| scope | 必做 |
| --- | --- |
| docs / harness | `safety-check.ps1 -Scope docs -DryRun` + `verify-local.ps1 -Scope docs` + `git diff --check` |
| backend | `mvn -f backend/pom.xml test` + `mvn -f backend/pom.xml -DskipTests package` + `safety-check.ps1 -Scope backend` + 后端 health + 容器 reload 验证 |
| frontend | `npm --prefix frontend run build` + `npm --prefix frontend run test` + `safety-check.ps1 -Scope frontend` + 前端 healthz |
| full | backend + frontend + compose health + API health + 业务 smoke |
| deploy | 部署前 commit 对齐 + 部署后 health + 日志 + API 验证 + DB 只读对账 |
| diagnosis | 复现 → 证据链 → 阶段性结论（未修复） |

详细命令矩阵见 `harness/rules/runbooks/governance/scope-command-matrix.md`。

**输出**：每条命令的 PASS / FAIL / SKIP 表 + 证据路径。

**禁止**：用 `should be OK` 替代命令输出；把 SKIP 当 PASS。

---

## Gate 5 — Evidence Gate

**目的**：留下可复现、可审计的证据。

**必做动作**：

1. 生成或覆盖 `harness/reports/current/latest-<report-key>.md`（docs-only 可参考 `harness/templates/docs-only-template.md`）。
2. 在 evidence 中写 retro 结论；仅当存在责任人、改进动作和验证方式时，按 `harness/templates/retro-summary-template.md` 生成独立 retro。
3. 更新 `harness/rules/state/snapshots/01-当前项目状态.md` 的相关状态（如有变化）。
4. 更新 `harness/rules/state/snapshots/DOMAIN_STATUS.md` 中本轮涉及领域的状态。
5. 更新 `harness/rules/changelog.md` 一行。
6. 更新 `harness/rules/state/debts/HARNESS_DEBT.md`（如本轮有新增 / 关闭 DEBT）。
7. 判断是否需要运行 `retire-content.ps1 -Action Plan`；归档/删除必须有 manifest。

**输出**：一份稳定 evidence；状态、债务、changelog 只在事实发生变化时更新，禁止为满足数量而制造文件。

**禁止**：未生成 evidence 就声明完成。

---

## Gate 6 — Git Gate

**目的**：把可提交的变更安全落地。

**必做动作**：

1. `git status --short`（必须显式记录输出）。
2. `git diff --check`（必须无输出）。
3. `git diff --cached --check`（无 staged；如有必须 check 通过）。
4. 通过 `agent-do.ps1 -ReportKey <key> -OwnedFiles '<path1>;<path2>'` 只暂存任务拥有的文件；手工回退时才逐文件 `git add -- <file>`。
5. 检查 commit message 含 type + scope（如 `docs(harness): ...` / `fix(order): ...`）。
6. 推送当前 upstream；无 upstream 时设置 `origin/<current-branch>`。`gitee` 只读，不自动推送。
7. 远端发布场景：完成候选提交、推送和 PR/CI 证据；等待 Merge Queue 合入 `release/real-pre` 与 Jenkins 队列。普通任务不得 SSH 或直接部署。

**输出**：commit hash + 远端同步结果 + 终态 (`DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN`)。

**禁止**：`--amend`、`--no-verify` 跳过 hook、`--force` 推送 main / master。

---

## Gate 7 — Exit Gate

**目的**：确认仓库可交接，下一 Agent 可直接接手。

**必做动作**（按 `harness/rules/governance/session-exit-gate.md`）：

1. **Build Clean**：`git diff --name-only` 不含 `backend/src/main/` `frontend/src/`（docs-only）。
2. **Test Clean**：本轮相关测试 PASS / 明确 BLOCKED。
3. **Progress Recorded**：CURRENT_STATE / DOMAIN_STATUS / HARNESS_CHANGELOG 已更新。
4. **Artifacts Clean**：无未登记的临时文件 / TODO / debug log。
5. **Startup Path Clean**：启动入口、端口、健康检查 URL 仍可用。
6. **Git State Clean**：dirty 已分类，unknown = 0；本轮 commit 已 push。

**输出**：`Session Exit Report`（按 `SESSION_EXIT_GATE.md` 模板）+ `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN`。

**禁止**：未通过 Exit Gate 写 DONE。

---

## 七 Gate 速记

| # | Gate | 关键词 | 失败代价 |
| --- | --- | --- | --- |
| 1 | Intake | 读 / 分类 / 范围 | 误改业务 |
| 2 | Scope | 允许 / 禁止 / dirty 分类 | dirty 膨胀 |
| 3 | Implementation | 小步 / 不混 | 难审核 |
| 4 | Verification | 命令 / 证据 | 假完成 |
| 5 | Evidence | 报告 / 状态 | 难复现 |
| 6 | Git | 安全 / 分批 | 部署脏 |
| 7 | Exit | 清洁 / 可交接 | 难接手 |

## 关联文档

- `harness/rules/policies/agent-contract.md`
- `harness/rules/governance/task-routing.md`
- `harness/rules/governance/COMPLETION_GATES.md`
- `harness/rules/governance/session-exit-gate.md`
- `harness/rules/governance/forbidden-scope.md`
- `harness/rules/skills/git/git-change-control.md`
- `harness/rules/skills/git/git-batch-submit.md`
- `harness/rules/skills/workflow/post-task-gc.md`
- `harness/rules/runbooks/governance/scope-command-matrix.md`
- `harness/rules/runbooks/governance/closeout-and-gc.md`
- `harness/rules/runbooks/governance/debt-governance.md`
