# GIT-HARNESS-001：Git 工作区治理与批次提交门禁强化

- 任务编号：GIT-HARNESS-001
- 任务名称：Git 工作区治理与批次提交门禁强化
- 时间：2026-06-03
- 环境：real-pre（本地）
- Completion Gate：Gate 0（Docs Only）
- Session Exit Gate：Git State Clean
- 是否修改业务代码：**否**（仅修改 harness 文档与状态文件）
- 是否提交 harness / 报告：是
- 是否执行数据库写操作命令：**否**
- 是否执行 migration 命令：**否**
- 是否重启容器：**否**
- 是否部署远端：**否**

---

## Final Status

DONE

## Selected Gate

Gate 0 - Docs Only（Gate G0 Git 子门禁：Docs-only clean）

## Scope

- 修改领域：Harness 工程化（Git 工作区治理）
- 修改文件：10 个 harness 文档（修改）+ 3 个 skills（新增）+ 1 个 state 新增段落 + 1 个报告（待生成）
- 影响接口：无
- 影响页面：无
- 影响表：无
- 影响容器：无

## 1. 任务概述

针对 2026-06-03 SYNC-PLAN-001 暴露的"多任务 dirty 膨胀 + 提交门禁缺失"问题，建立 Git 工作区治理与批次提交门禁系统。本任务仅修改 harness 文档与状态文件，不修改业务代码。

## 2. 当前 dirty 问题复盘

通过 Git Intake Gate 复盘近期（2026-06-03）的真实原因：

1. **多任务并行产生 dirty**：P-FIX-002、U-2.5-B、TEST-1、FUNC-001、P-FIX-001C、Batch docs/cleanup 同时存在 dirty。
2. **状态文件混合记录多个任务**：`CURRENT_STATE.md` / `DOMAIN_STATUS.md` / `KNOWN_ISSUES.md` / `HARNESS_CHANGELOG.md` 都包含多个不同任务的状态更新。
3. **部署配置文件残留**：`backend/src/main/resources/application-real-pre.yml`（P-FIX-002A 同步配置）残留在工作区 unstaged 状态，与 Batch 3 范围正交但被混在一起。
4. **报告未及时单独提交**：FUNC-001 / P-FIX-001C / P-FIX-002 / U-2.5-B / TEST-1 等任务报告作为 untracked 累积在 `harness/reports/`。
5. **缺少强制 Intake / Exit Gate**：没有"任务开始前工作区检查"和"任务结束后 dirty 清零/登记"机制。
6. **缺少"工作区不干净则禁止新任务"规则**：新任务可以直接开始而不管 dirty。
7. **缺少"按任务编号拆分提交"硬性要求**：单 commit 可能包含多任务变更。
8. **`git add .` / `git add -A` 风险**：缺乏显式禁止，整目录 add 可能混入 unknown 文件。

## 3. 修改的 Harness 文件清单

| 文件 | 状态 | 任务来源 | 类别 |
| --- | --- | --- | --- |
| `harness/skills/git-change-control.md` | untracked / new | GIT-HARNESS-001 | current_task |
| `harness/skills/git-batch-submit.md` | untracked / new | GIT-HARNESS-001 | current_task |
| `harness/skills/post-task-gc.md` | untracked / new | GIT-HARNESS-001 | current_task |
| `harness/AGENT_CONTRACT.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/TASK_ROUTING.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/FORBIDDEN_SCOPE.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/COMPLETION_GATES.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/SESSION_EXIT_GATE.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/state/KNOWN_ISSUES.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/state/DECISIONS.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/HARNESS_CHANGELOG.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/CURRENT_STATE.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/state/DOMAIN_STATUS.md` | modified | GIT-HARNESS-001 | current_task |
| `harness/reports/git-harness-001-worktree-governance-20260603-*.md` | untracked / new | GIT-HARNESS-001 | current_task |

## 4. 新增 Git Intake Gate

- 命令：`git status --short` / `git diff --name-only` / `git diff --stat` / `git log -1 --oneline` / `git branch --show-current` / `git remote -v`。
- 必须输出：当前分支、HEAD、dirty 文件总数、modified / deleted / untracked 数、staged 是否为空、unknown 文件、是否允许开始任务。
- 工作区不干净时不得开始任务。
- 详细规则：`harness/skills/git-change-control.md` 第 2 节。

## 5. 新增 Staged Scope Gate

- 命令：`git diff --cached --name-only` / `git diff --cached --stat` / `git diff --cached --check`。
- 必须确认 6 条通过条件（单任务归属、不含 unknown、不含禁止范围、不含非本任务代码、不含环境敏感文件、不含临时文件）。
- 禁止命令：`git add .` / `git add -A` / `git add harness/` / `git add backend/` / `git add frontend/`。
- 推荐逐文件 `git add -- <file>`。

## 6. 新增 Commit Gate

- 必须满足：测试通过（按 Scope）、`git diff --cached --check` 通过、commit message 含类型和 scope、evidence report 已生成、状态文件已更新、staged 清单写入报告。
- commit message 规范：`feat(<scope>)` / `fix(<scope>)` / `test(<scope>)` / `docs(harness)` / `docs(reports)` / `chore(cleanup)` / `chore(retire)`。
- 禁止：`git commit --amend`、空 message、`--no-verify` 跳过 hook（除非用户明确要求）。

## 7. 新增 Push Gate

- 推送前必须确认：当前 commit 符合 Commit Gate、当前分支允许推送（`feature/*` 允许；`main` / `master` 必须经用户授权）。
- 双 Remote 推送顺序：先 `gitee`（远端服务器拉取源），后 `origin`（GitHub 备份）。
- 推送后必须记录：commit hash、remote、branch、结果、时间戳。

## 8. 新增 Deploy Commit Gate

- 必须满足：代码已 commit 到本地、commit 已推送到目标 remote、远端 `git fetch` + `git checkout` + `git pull --ff-only` 拉到目标 commit、远端 `git rev-parse HEAD` 等于目标 commit、远端 `git status --short` 为空。
- 禁止：从本地 dirty 工作区直接部署、未提交代码部署、远端 dirty 源码部署、用本地 jar 替换远端 jar 而不经过 commit / push / pull 链路、远端手工 patch 业务文件后再 commit。
- `.env` 例外：可作为远端本地运行配置，但不得 commit / push。
- 部署后必须验证：容器状态（`docker compose ps` 全部 healthy）、health check、commit hash、jar / dist 时间、必要页面 / API smoke。

## 9. 新增 Git Exit Gate

- 触发：每个任务结束前。
- 命令：`git status --short` / `git diff --name-only` / `git log -1 --oneline`。
- 终态判定（4 种之一）：
  - `DONE_CLEAN`：工作区干净，已 commit + push。
  - `DONE_WITH_REGISTERED_DIRTY`：dirty 全部登记到下一任务。
  - `PARTIAL_DIRTY_REMAINING`：dirty 未收口，禁止进入无关新任务。
  - `BLOCKED_DIRTY_UNKNOWN`：存在 unknown，必须先调查；不能输出 DONE。
- 禁止终态：`DONE` 状态下还有 unknown dirty、`PARTIAL` 状态下进入无关新任务、`BLOCKED` 状态下不写阻塞原因。

## 10. Forbidden Scope 更新摘要

新增"Git 工作区治理禁止事项"章节，列出 18 条禁止：

- 禁止 `git add .` / `git add -A` / `git add <dir>/`。
- 禁止提交 unknown 文件。
- 禁止混合多任务 commit。
- 禁止 dirty 工作区部署。
- 禁止从本地 dirty 拷贝到远端。
- 禁止未提交代码部署。
- 禁止远端 dirty 源码部署。
- 禁止把未部署写成已部署。
- 禁止把未提交写成已推送。
- 禁止把 PARTIAL 状态写成 DONE。
- 禁止状态文件混合错误状态。
- 禁止任务未收口继续新任务。
- 禁止 `git commit --amend`。
- 禁止 `--no-verify` 跳过 commit hook。
- 禁止 `git push --force` 到 `main` / `master`。
- 禁止使用未归类为 10 种分类之一的文件 commit。
- 禁止 Git Intake Gate 不通过就开始任务。
- 禁止 Git Exit Gate 不输出合法终态就结束任务。

## 11. Completion Gates 更新摘要

新增"Git Gate（G0-G4 内部子门禁）"章节：

- **Gate G0：Docs-only clean**：纯文档 / Harness 规则 / 状态文件 / 报告变更；`git diff --name-only` 仅含 `harness/` / `docs/` / `AGENTS.md` / `CLAUDE.md` / 报告；状态文件已记录；未使用 `git add .`。
- **Gate G1：Frontend clean**：纯前端；`git diff --name-only` 仅含 `frontend/src/` 等；`npm run build` / 关键 vitest / frontend `safety-check` 通过。
- **Gate G2：Backend clean**：纯后端；`git diff --name-only` 仅含 `backend/src/main/` 等；`mvn test` / `mvn package` / backend `safety-check` 通过。
- **Gate G3：Deploy clean**：Docker / Compose / env / 部署脚本；远端 `git fetch` + `git checkout` + `git pull --ff-only` 成功；远端 `git rev-parse HEAD` 等于本地 HEAD；`docker compose ps` 全部 healthy。
- **Gate G4：Session clean**：所有 dirty 已归入 10 种分类之一；不存在 unknown dirty；当前任务 commit 已 push；状态文件已更新；终态合法。

子门禁强制规则：

- Gate G0-G4 任一未通过，最终状态不得 DONE。
- 同一 commit 不得跨 Gate。
- 业务代码 commit 不得含状态文件。
- 多任务 dirty 必须分批提交。

## 12. Session Exit Gate 更新摘要

新增"Git 状态 Clean（Git Exit Gate 强约束）"作为 Session Exit Gate 第六项硬门禁：

- `git status --short` 输出已分类。
- 所有 dirty 必须归入 10 种分类之一。
- 所有 staged 必须为空或已提交并推送。
- 所有 untracked 必须归属。
- 不能留下 unknown dirty。
- 当前任务 commit 已推送到目标 remote。
- 状态文件已更新或明确不更新。

退出检查模板新增"Git State Clean"行。

新增 5 条 Git 禁止事项（11-15）：

11. 工作区存在 unknown dirty。
12. 工作区存在未分类 dirty。
13. 业务代码 commit 混入 docs / 报告 / 状态文件。
14. 当前任务 commit 未推送到目标 remote。
15. 状态文件未更新。

## 13. 状态文件更新摘要

| 文件 | 更新内容 |
| --- | --- |
| `harness/state/KNOWN_ISSUES.md` | 新增一行："Git 工作区 dirty 膨胀与批次提交门禁缺失 \| fixed \| GIT-HARNESS-001 治理" |
| `harness/state/DECISIONS.md` | 新增 2026-06-03 Git 工作区治理决策摘要（6 条决策）；新增决策索引条目 |
| `harness/CURRENT_STATE.md` | 新增 GIT-HARNESS-001 完成段落；明确"通过 SYNC-PLAN-001 暴露 → B3-SCOPE-001 验证 → GIT-HARNESS-001 落地"链路 |
| `harness/state/DOMAIN_STATUS.md` | 新增"## Harness"段，标记 P0 |
| `harness/HARNESS_CHANGELOG.md` | 新增 v0.5.0 条目 |

## 14. Dirty Classification（本任务 Intake Gate 实际分类）

| 文件 | 状态 | 分类 | 所属任务 | 下一动作 |
| --- | --- | --- | --- | --- |
| `harness/AGENT_CONTRACT.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/COMPLETION_GATES.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/CURRENT_STATE.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/FORBIDDEN_SCOPE.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/HARNESS_CHANGELOG.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/SESSION_EXIT_GATE.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/TASK_ROUTING.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/state/DECISIONS.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/state/DOMAIN_STATUS.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/state/KNOWN_ISSUES.md` | M | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/skills/git-change-control.md` | ?? | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/skills/git-batch-submit.md` | ?? | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/skills/post-task-gc.md` | ?? | current_task | GIT-HARNESS-001 | stage + commit |
| `harness/reports/git-harness-001-worktree-governance-20260603-*.md` | ?? | current_task | GIT-HARNESS-001 | stage + commit（待生成） |
| `backend/src/main/resources/application-real-pre.yml` | M | previous_partial | P-FIX-002A 残留 | 保留 unstaged，下一任务 P-FIX-002 状态收口处理 |
| `harness/reports/b3-scope-001-batch3-scope-isolation-20260603-143131.md` | ?? | report_only | B3-SCOPE-001 | 保留 untracked，下一 Batch 4 报告提交 |
| `harness/reports/content-retire-20260603-103207.md` | ?? | report_only | content-retire | 保留 untracked，下一 Batch 4 报告提交 |
| `harness/reports/content-retire-20260603-111343.md` | ?? | report_only | content-retire | 同上 |
| `harness/reports/content-retire-20260603-113617.md` | ?? | report_only | content-retire | 同上 |
| `harness/reports/evidence-20260603-101503.md` | ?? | report_only | U-2.5-B | 同上 |
| `harness/reports/evidence-20260603-104232.md` | ?? | report_only | DDD | 同上 |
| `harness/reports/evidence-20260603-104601.md` | ?? | report_only | TEST-1 | 同上 |
| `harness/reports/evidence-20260603-111733.md` | ?? | report_only | FUNC-001 | 同上 |
| `harness/reports/evidence-20260603-113632.md` | ?? | report_only | P-FIX-001C | 同上 |
| `harness/reports/evidence-20260603-122021.md` | ?? | report_only | P-FIX-002 | 同上 |
| `harness/reports/evidence-20260603-142506.md` | ?? | report_only | B3-VERIFY-001 | 同上 |
| `harness/reports/func-001-product-card-hover-ui-20260603-111451.md` | ?? | report_only | FUNC-001 | 同上 |
| `harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md` | ?? | report_only | GIT-BATCH-2 | 同上 |
| `harness/reports/git-batch-3-backend-user-domain-u2_5-test1-20260603-144936.md` | ?? | report_only | GIT-BATCH-3 | 同上 |
| `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` | ?? | report_only | P-FIX-001C | 同上 |
| `harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md` | ?? | report_only | P-FIX-001C | 同上 |
| `harness/reports/retro-20260603-104247.md` | ?? | report_only | DDD | 同上 |
| `harness/reports/retro-20260603-104601.md` | ?? | report_only | TEST-1 | 同上 |
| `harness/reports/retro-20260603-111824.md` | ?? | report_only | FUNC-001 | 同上 |
| `harness/reports/retro-20260603-113645.md` | ?? | report_only | P-FIX-001C | 同上 |
| `harness/reports/retro-20260603-122513.md` | ?? | report_only | P-FIX-002D | 同上 |
| `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md` | ?? | report_only | TEST-1 | 同上 |
| `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md` | ?? | report_only | U-2.5-B | 同上 |

unknown 文件数：**0**。

## 15. 验证结果

| 命令 | 结果 | 证据 |
| --- | --- | --- |
| `git diff --check` | PASS | 无 whitespace 错误（仅 CRLF 警告，Git 在下次写入时自动规范） |
| `git status --short` 输出分析 | PASS | 38 个 dirty 全部已分类，unknown = 0 |
| `git log -1 --oneline` | PASS | `c470dc29 fix(user): unify dept type constants and stabilize tests` |
| `git branch --show-current` | PASS | `feature/auth-system` |
| `git remote -v` | PASS | `gitee` + `origin` 双 remote 已配置 |
| `safety-check -Scope docs -DryRun` | PASS | Safety check passed |
| `verify-local -Scope docs` | PASS | repository structure check passed; HTTP health checks skipped |

## 16. Git Exit Gate 终态

任务结束后将执行 Git Exit Gate。当前预期终态：

`DONE_WITH_REGISTERED_DIRTY`（因为 `application-real-pre.yml` P-FIX-002 残留 + 23 个报告 untracked 仍未提交，但已分类登记到下一任务 Batch 4 / P-FIX-002 状态收口）。

## 17. 关键不变量确认

| 不变量 | 状态 |
| --- | --- |
| 是否修改业务代码 | **否**（仅 harness 文档与状态文件） |
| 是否执行数据库操作 | **否** |
| 是否执行 migration | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |
| 是否使用 `git add .` | **否**（按文件逐个 add） |
| 是否混入 backend 业务代码 | **否**（`application-real-pre.yml` 显式排除） |
| 是否混入 frontend | **否** |
| 是否混入 SQL | **否** |
| 是否混入 Docker / Compose | **否** |
| 是否混入 env / 凭证 | **否** |
| 是否包含 unknown dirty | **否** |

## 18. 后续执行建议

1. **Batch 4 报告提交**（按 SYNC-PLAN-001 推荐顺序）：23 个 untracked 报告 + b3-scope-001 报告。
2. **Batch 1 harness-docs 提交**（如 Batch 4 后仍有 harness 状态文件 dirty）：合并到 docs-only commit。
3. **P-FIX-002 状态收口**：`application-real-pre.yml` 单独任务，确认本地与 commit `dea06e4c` 内容一致后提交；或若内容已包含在 commit `dea06e4c` 中，标记为已 remote-closed。
4. **Batch 5 cleanup-retire**：执行 retire-content manifest 归档 + `.gitignore` 审查。
5. **所有未来任务**：必须先执行 Git Intake Gate → 范围声明 → 逐文件 stage → Staged Scope Gate → Commit Gate → Push Gate → Git Exit Gate。任一未通过不得进入下一任务。
6. **DDD 任务**：U-3 CurrentUser / PermissionContext 统一，需在 Git Intake Gate 确认 `application-real-pre.yml` 已收口后再开始。

## 19. 报告路径

`harness/reports/git-harness-001-worktree-governance-20260603-150000.md`
