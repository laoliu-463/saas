# Git Change Control

> 本文件定义 Harness 中所有 Git 相关强约束。任何 Agent 任务在开始、修改、提交、推送、部署、结束前必须按本文件执行对应 Gate。

## 1. 总原则

1. **任务开始前**：必须执行 Git Intake Gate。
2. **任务执行中**：只能改当前任务 Allowed Change Set 范围。
3. **任务结束前**：必须执行 Git Exit Gate。
4. **提交前**：必须执行 Staged Scope Gate。
5. **推送前**：必须执行 Push Gate。
6. **部署前**：必须执行 Deploy Commit Gate。
7. **会话退出前**：必须执行 Session Exit Gate 的 Git 检查。

任何 dirty 文件未分类为以下十种之一（`current_task / previous_partial / docs_state / report_only / frontend / backend / sql_migration / docker_deploy / cleanup_retire / unknown`），不得进入任何 commit / push / deploy。

任何任务不得使用 `git add .` / `git add -A` / `git add <dir>/`。文件必须逐个 `git add -- <file>` 添加，且必须先经过 Staged Scope Gate。

每个任务必须做到以下 Git 终态之一，且与 `DONE / PARTIAL / BLOCKED / ROLLBACK_REQUIRED` 一致：

- `DONE_CLEAN`：工作区干净，已提交并推送。
- `DONE_WITH_REGISTERED_DIRTY`：dirty 全部登记到下一任务。
- `PARTIAL_DIRTY_REMAINING`：存在未收口 dirty。
- `BLOCKED_DIRTY_UNKNOWN`：存在 unknown dirty，必须先清理。

## 2. Git Intake Gate

### 2.1 触发

每个任务开始前（包括 docs-only）必须执行。

### 2.2 命令

```powershell
git status --short
git diff --name-only
git diff --stat
git log -1 --oneline
git branch --show-current
git remote -v
```

### 2.3 必须输出

- 当前分支
- 当前 HEAD（commit hash + 短说明）
- dirty 文件总数
- modified 数
- deleted 数
- untracked 数
- staged 是否为空
- 是否存在来源不明文件
- 是否允许开始任务（YES / NO + 原因）

### 2.4 工作区不干净时的处理

| dirty 状态 | 处理 |
| --- | --- |
| 与当前任务无关，但归属已明确（前置任务遗留） | 当前任务不得开始；要么先提交前置任务，要么走 PARTIAL 继承 |
| 来源不明（unknown） | 当前任务不得开始；必须先执行 GIT-CLEANUP 或 GIT-SCOPE 调查 |
| 仅状态文件 / 报告 / 已废弃内容 | 可在 Git Intake Gate 内做 Dirty Classification，并归入 docs_state / report_only / cleanup_retire |
| 仅环境敏感文件 | 立即停止任务；按 `FORBIDDEN_SCOPE.md` 处理 |

不允许"先改了再说"。必须先 Intake Gate，再做修改。

### 2.5 Intake Gate 报告

每次 Intake Gate 执行后必须输出：

```text
## Git Intake
- branch: feature/auth-system
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

## 3. Allowed Change Set

每个任务开始时必须明确 Allowed Change Set（本次任务允许修改的文件目录与命名空间）。

| Scope | 允许目录 | 禁止目录 |
| --- | --- | --- |
| `docs` | `harness/`, `docs/`, `AGENTS.md`, `CLAUDE.md`, 报告 | `backend/src/`, `frontend/src/`, SQL, Docker, env, `runtime/` |
| `backend` | `backend/src/main/`, `backend/src/test/`, `backend/pom.xml` | `frontend/`, `harness/`, `docs/`, SQL 文件, Docker |
| `frontend` | `frontend/src/`, `frontend/package.json`, `frontend/vite.config.ts`, E2E | `backend/`, `harness/`, `docs/`, SQL, Docker |
| `sql` | `backend/src/main/resources/db/`, `scripts/db/` | 业务 Java 代码, Vue, Docker, env |
| `docker_deploy` | `docker-compose*.yml`, `Dockerfile*`, `deploy-*.ps1` | 业务 Java/Vue 代码, env, SQL |
| `cleanup_retire` | `harness/reports/`, `harness/archive/`, `.gitignore` | 任何业务代码 / 配置 / 容器 / env |

修改前如发现超出 Allowed Change Set 的文件，必须立即回滚或停止任务。

## 4. Dirty Classification

所有 dirty 文件必须归入以下类型之一：

| 类型 | 含义 | 是否允许 stage | 是否允许提交 | 是否需要部署 |
| --- | --- | --- | --- | --- |
| `current_task` | 当前任务修改文件 | 是 | 是 | 按 Gate |
| `previous_partial` | 前置任务遗留（已 PARTIAL） | 仅在当前任务明确继承时 | 仅在 Batch 计划中 | 按 Batch |
| `docs_state` | 状态文件变更 | 是 | 是（仅 docs 任务） | 否 |
| `report_only` | 报告生成 | 是 | 是 | 否 |
| `frontend` | 前端文件 | 是 | 是（仅 frontend 任务） | 视任务 |
| `backend` | 后端文件 | 是 | 是（仅 backend 任务） | 视任务 |
| `sql_migration` | SQL migration | 是 | 是（仅 sql 任务） | 视任务 |
| `docker_deploy` | Docker / 部署 | 是 | 是（仅 deploy 任务） | 是 |
| `cleanup_retire` | 清理 / 归档 | 是 | 是（仅 cleanup 任务） | 否 |
| `unknown` | 来源不明 | **否** | **否** | **否** |

分类输出表必须包含以下列：

- 文件路径
- Git 状态（M / D / A / R / ??）
- 分类（十种之一）
- 所属任务编号（如 GIT-BATCH-N / FUNC-001 / U-2.5-B 等）
- 是否允许 stage
- 是否允许提交
- 是否需要部署
- 风险说明

`unknown` 文件必须满足：

- 禁止 stage。
- 禁止 commit。
- 禁止 push。
- 禁止 deploy。
- 必须单独调查（创建 `harness/reports/unknown-dirty-investigation-*.md`）。
- 不得混入任何 batch。
- 调查完成前当前任务最终状态只能是 `BLOCKED_DIRTY_UNKNOWN`。

## 5. Staged Scope Gate

### 5.1 触发

每次 `git add` 后、每次 `git commit` 前必须执行。

### 5.2 命令

```powershell
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
```

### 5.3 必须确认

- staged 文件全部属于同一任务或同一 batch。
- staged 不包含 `unknown`。
- staged 不在 `FORBIDDEN_SCOPE.md` 范围。
- staged 不含非本任务代码。
- staged 不含环境敏感文件（`.env`、`.pem`、`.key`、凭证）。
- staged 不含本地临时文件（`*.tmp`、`.bak`、`debug-*`、`test-output-*`、`nul` 等）。
- `git diff --cached --check` 无输出（无空白错误、无合并冲突标记）。

### 5.4 禁止命令

以下命令默认禁止（除非某个专项任务明确允许整目录，并在报告中证明目录内全是本任务文件）：

```powershell
git add .
git add -A
git add harness/
git add backend/
git add frontend/
```

### 5.5 推荐 stage 模式

逐文件 `git add -- <file>`，并在每次 add 后用 `git diff --cached --name-only` 复核。

如需批量 add，限定为已确认归属的具体文件列表：

```powershell
git add -- <file1> <file2> <file3>
```

## 6. Commit Gate

### 6.1 触发

每次 `git commit` 前。

### 6.2 必须满足

- 对应 Scope 的测试通过（backend → `mvn test` / frontend → `npm run build` + 关键 vitest / docs → safety-check）。
- `git diff --cached --check` 通过。
- commit message 符合规范（见 6.3）。
- evidence report 已生成（`harness/reports/evidence-*.md`）。
- 状态文件已更新或明确说明不更新。
- staged 文件清单已写入报告。

### 6.3 commit message 规范

```text
feat(<scope>): <描述>
fix(<scope>): <描述>
test(<scope>): <描述>
docs(harness): <描述>
docs(reports): <描述>
chore(cleanup): <描述>
chore(retire): <描述>
```

scope 命名建议：

- `product-ui` / `product` / `talent` / `sample` / `user` / `order` / `performance` / `config` / `analytics` / `auth`
- `harness`（仅 harness 规则与状态）
- `reports`（仅任务报告、evidence、retro）
- `cleanup` / `retire`（仅清理归档）

正文可多行，但必须包含：任务编号、修改摘要、验证摘要、剩余风险。

### 6.4 禁止 commit

- 包含 unknown 文件。
- 包含跨 Scope 文件。
- 包含环境敏感文件。
- 临时文件（`*.tmp`、`.bak`、`nul`、`debug-*`、`test-output-*`）。

## 7. Push Gate

### 7.1 触发

每次 `git push` 前。

### 7.2 必须确认

- 当前 commit 符合 Commit Gate。
- 当前分支允许推送（`feature/*` 允许；`main` / `master` 必须先经用户授权）。
- 远端 remote 配置正确。

### 7.3 命令

```powershell
git remote -v
git branch --show-current
git log -1 --oneline
```

### 7.4 双 Remote 推送

本项目使用 Gitee 作为远端服务器拉取源，GitHub 作为备份：

```powershell
git push gitee feature/<branch>
git push origin feature/<branch>
```

运行态部署相关提交必须先 push 到 `gitee`。GitHub 同步推送仅作备份。

### 7.5 推送后记录

每次 push 必须在报告中记录：

- commit hash
- remote（`gitee` / `origin`）
- branch
- 结果（PASS / FAIL + 错误信息）
- 时间戳

## 8. Deploy Commit Gate

### 8.1 触发

任何远端 / 本地部署前。

### 8.2 必须满足

- 代码已经 commit 到本地。
- commit 已推送到目标 remote。
- 远端服务器通过 `git fetch` + `git checkout` + `git pull --ff-only` 拉到目标 commit。
- 远端 `git rev-parse HEAD` 等于目标 commit。
- 远端 `git status --short` 为空（clean）。
- jar / dist / 配置来源与目标 commit 一致。

### 8.3 禁止

- 禁止从本地 dirty 工作区直接 `scp` / `rsync` 文件到远端部署。
- 禁止使用未提交代码部署。
- 禁止远端 dirty 源码部署。
- 禁止用本地 jar 替换远端 jar 而不经过 commit / push / pull 链路。
- 禁止在远端手工 patch 业务文件后再 commit。

### 8.4 .env 例外

`.env` / `.env.real-pre` / `.env.test` 可以是远端本地运行配置，但必须：

- 在 `harness/environment/real-pre-env.md` 中说明远端实际值。
- 每次部署后通过 `deploy-remote.ps1` / `verify-local.ps1` 写入 evidence。
- 不得 commit / push 到任何 remote。

### 8.5 部署后必须验证

- 容器状态（`docker compose ps` 全部 healthy）。
- health check（backend `/api/system/health`、frontend `/healthz`）。
- commit hash（远端 `git rev-parse HEAD`）。
- jar / dist 时间（与 commit 时间一致）。
- 必要页面 / API smoke。

## 9. Git Exit Gate

### 9.1 触发

每个任务结束前（包括本任务的最终收口）。

### 9.2 命令

```powershell
git status --short
git diff --name-only
git log -1 --oneline
```

### 9.3 终态判定

| 终态 | 含义 | 允许后续 |
| --- | --- | --- |
| `DONE_CLEAN` | 工作区干净；当前任务已 commit + push | 进入下一任务 |
| `DONE_WITH_REGISTERED_DIRTY` | dirty 已分类并登记到下一任务 | 下一任务开始前在 Git Intake Gate 确认继承 |
| `PARTIAL_DIRTY_REMAINING` | dirty 未收口 | 禁止进入无关新任务；只允许继续当前任务或收口任务 |
| `BLOCKED_DIRTY_UNKNOWN` | 存在 unknown | 必须先调查；不能输出 DONE |

### 9.4 报告必须包含

- Git Exit Gate 终态
- dirty 文件清单
- 每个文件的分类（按 4 Dirty Classification）
- 后续建议（提交 / 继续 / 调查 / 收口）

### 9.5 禁止终态

- 不允许 `DONE` 状态下还有 unknown dirty。
- 不允许 `PARTIAL` 状态下进入无关新任务。
- 不允许 `BLOCKED` 状态下不写阻塞原因。

## 10. Unknown Dirty Policy

### 10.1 判定

当 dirty 文件不满足以下任一条件时归为 `unknown`：

- 有明确任务编号。
- 有明确提交批次（Batch N）。
- 有明确分类（按 4 Dirty Classification）。
- 有明确继承任务编号（PARTIAL 继承）。

### 10.2 处理流程

1. 立即停止任何 commit / push / deploy。
2. 在报告中记录 `unknown_files: [...]`。
3. 生成调查清单：
   - 文件路径
   - 修改时间
   - 最近引用 commit
   - 关联任务候选
4. 至少人工 / 旧 report 引用 + git log + git blame 三项调查后才能重新分类。
5. 调查完成前最终状态只能是 `BLOCKED_DIRTY_UNKNOWN`。

### 10.3 禁止

- 不允许把 unknown 强行归类为 `current_task` / `previous_partial`。
- 不允许把 unknown 与其他 batch 一起提交。
- 不允许"先 commit 再说，回头再查"。

## 11. Rollback Policy

### 11.1 触发

- 部署后核心 API 失败。
- 远端 health check 失败。
- 关键业务链路回归。
- 用户明确要求回滚。

### 11.2 流程

1. 立即在 `harness/reports/` 生成 `rollback-<task>-<timestamp>.md`。
2. 远端 `git revert <commit-hash>` 或回滚到上一 commit。
3. 重新构建 / 重启容器。
4. 重新验证 health + smoke。
5. 更新 `CURRENT_STATE.md` / `DOMAIN_STATUS.md` / `KNOWN_ISSUES.md`。
6. 更新 `HARNESS_CHANGELOG.md` 的最终状态为 `ROLLBACK_REQUIRED`。
7. 进入 Git Exit Gate `BLOCKED` 状态，等待新任务。

### 11.3 禁止

- 不允许仅回滚远端而不回滚本地 commit。
- 不允许跳过 evidence 报告就回滚。
- 不允许在回滚后保留 unknown dirty。

## 12. 与其他文件的关系

- `harness/AGENT_CONTRACT.md`：必须遵守总规则。
- `harness/FORBIDDEN_SCOPE.md`：所有禁止项必须遵守。
- `harness/COMPLETION_GATES.md`：本文件定义的 Gate 是 Gate 0-4 内部的 Git 子门禁。
- `harness/SESSION_EXIT_GATE.md`：会话退出必须执行 Git Exit Gate。
- `harness/TASK_ROUTING.md`：任务路由必须先经过 Git Intake。
- `harness/skills/git-batch-submit.md`：批次提交的具体步骤。
- `harness/skills/post-task-gc.md`：任务后 Git 清理步骤。
