# Git Batch Submit

> 本文件定义 Harness 中批次提交（git batch submit）的具体操作流程。适用于多任务累积 dirty 后的分批提交场景。
> 配合 `harness/skills/git-change-control.md` 使用。

## 1. 适用场景

- 多个任务积累 dirty / untracked，需要分批提交。
- 多领域 dirty 同时存在（backend / frontend / docs / cleanup）。
- 部署相关业务代码必须与 docs-only 分开。
- 任何使用 `git batch submit` 作为任务编号（`GIT-BATCH-N`）的任务。

## 2. 批次划分原则

按以下顺序划分批次，**业务代码批次和 docs / reports 批次必须分开**：

| 顺序 | 批次类型 | 内容 | 部署 |
| --- | --- | --- | --- |
| 1 | reports 报告 | `harness/reports/*.md` | 否 |
| 2 | harness-docs | `harness/AGENT_CONTRACT.md`、`CURRENT_STATE.md`、`HARNESS_CHANGELOG.md`、`COMPLETION_GATES.md`、`SESSION_EXIT_GATE.md`、`QUALITY_LEDGER.md`、`TASK_ROUTING.md`、`FORBIDDEN_SCOPE.md`、`state/*.md`、`skills/*.md` | 否 |
| 3 | cleanup-retire | 归档 / 删除 / `.gitignore` | 否 |
| 4 | backend | `backend/src/main/`、`backend/src/test/`、`backend/pom.xml` | 视任务 |
| 5 | frontend | `frontend/src/`、`frontend/package.json`、`frontend/vite.config.ts`、E2E | 视任务 |
| 6 | sql | `backend/src/main/resources/db/`、migration | 视任务 |
| 7 | docker_deploy | `docker-compose*.yml`、`Dockerfile*`、`deploy-*.ps1` | 是 |

每个批次必须有独立任务编号 `GIT-BATCH-N`，与业务任务编号解耦。

## 3. 批次提交步骤

### 3.1 阶段 A：Intake 与分类

```powershell
git status --short
git diff --name-only
git log -1 --oneline
git branch --show-current
git remote -v
```

输出 Dirty Classification 表（按 `git-change-control.md` 第 4 节）。

### 3.2 阶段 B：起草 Batch 计划

- 批次编号 `GIT-BATCH-N`
- 批次名 `<scope>-<purpose>`
- 文件清单（含分类、所属任务编号、是否需部署）
- 验证命令
- 风险说明
- 回滚方案

写到 `harness/reports/git-batch-<N>-<scope>-<purpose>-<timestamp>.md`，并按需在 `sync-plan-*.md` 中追加。

### 3.3 阶段 C：scope 隔离

逐文件 `git add -- <file>`，每次 add 后：

```powershell
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
```

禁止命令：

```powershell
git add .
git add -A
git add harness/
git add backend/
git add frontend/
```

### 3.4 阶段 D：审查

按 5.审查规则（见下文）逐项过。

### 3.5 阶段 E：commit

```powershell
git commit -m "<type>(<scope>): <task-id> <description>"
```

正文（多行）必须包含：

- 任务编号
- 修改摘要
- 验证摘要
- 剩余风险
- 关联 report 路径

### 3.6 阶段 F：双 Remote 推送

```powershell
git push gitee feature/<branch>
git push origin feature/<branch>
```

### 3.7 阶段 G：报告收口

- 更新 `harness/CURRENT_STATE.md`
- 更新 `harness/state/DOMAIN_STATUS.md`（如涉及领域）
- 更新 `harness/HARNESS_CHANGELOG.md`
- 生成 `harness/reports/retro-*.md`

## 4. 文件归属分类

每个文件必须明确归属：

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| 文件路径 | 完整相对路径 | `frontend/src/views/product/ProductLibrary.vue` |
| Git 状态 | M / D / A / R / ?? | M |
| 分类 | 10 种之一 | `frontend` |
| 所属任务 | 任务编号或 Batch | `P-FIX-001C` |
| 所属 Batch | 批次编号 | `GIT-BATCH-2` |
| 是否需 stage | true / false | true |
| 是否需提交 | true / false | true |
| 是否需部署 | true / false | true（frontend） |
| 风险说明 | 简短文字 | 无 |

## 5. 审查规则

每次 commit 前必须审查以下 9 项：

1. staged 文件全部属于单一任务或单一 batch。
2. 不含 `unknown` 文件。
3. 不含禁止范围（`FORBIDDEN_SCOPE.md`）。
4. 不含非本任务代码。
5. 不含环境敏感文件（`.env`、`.pem`、`.key`、凭证、Token、密码）。
6. 不含本地临时文件（`*.tmp`、`.bak`、`debug-*`、`test-output-*`、`nul`）。
7. `git diff --cached --check` 无输出。
8. scope 与任务 scope 一致（如 `frontend` 任务不得含 backend Java）。
9. commit message 含类型和 scope，且符合规范。

## 6. `git diff --cached` 审查规则

提交前必读三个 `git diff --cached` 输出：

### 6.1 `--name-only` 审查

```powershell
git diff --cached --name-only
```

确认文件清单与计划完全一致。

### 6.2 `--stat` 审查

```powershell
git diff --cached --stat
```

确认行数和文件数与计划量级相符（异常大变更必须拆分或重新评估）。

### 6.3 `--check` 审查

```powershell
git diff --cached --check
```

无输出 = 通过。任何输出 = 必须修复后再提交。

### 6.4 完整 diff 抽样审查

对每个文件至少扫一次 `git diff --cached -- <file>`：

- 确认无残留 debug 语句（`console.log`、`System.out.println`、`TODO`、`FIXME`、无解释）。
- 确认无 `nul` / `debug-*` / `.bak` 等本地污染。
- 确认无硬编码 URL、IP、Token、密钥、密码。
- 确认业务逻辑与任务描述一致。

## 7. commit message 规范

### 7.1 格式

```text
<type>(<scope>): <task-id> <short description>

<body>
```

### 7.2 type

`feat` / `fix` / `refactor` / `test` / `docs` / `chore` / `perf` / `ci`

### 7.3 scope

- `product-ui` / `product` / `talent` / `sample` / `user` / `user-domain` / `order` / `performance` / `config` / `analytics`
- `harness`（仅 harness 规则）
- `reports`（仅任务报告 / evidence / retro）
- `cleanup`（清理 / 归档）
- `batch-<N>`（批次提交）

### 7.4 任务编号

- 业务任务：`U-2.5-B`、`TEST-1`、`FUNC-001`、`P-FIX-001C`、`P-FIX-002`
- 批次任务：`GIT-BATCH-N`
- Harness 任务：`GIT-HARNESS-001`、`GIT-CLEANUP-001`

### 7.5 示例

```text
feat(product-ui): product card hover expand and library load-more pagination
fix(user-domain): unify dept_type constants and fix backend test fixtures
docs(harness): GIT-HARNESS-001 add git worktree governance gates
chore(cleanup): retire archived reports and update gitignore
```

### 7.6 禁止

- 不允许 `git commit --amend`（除非用户明确要求）。
- 不允许 `git commit -m ""`（空 message）。
- 不允许用 `--no-verify` 跳过 hook（除非用户明确要求）。
- 不允许 commit 后再补 message。

## 8. Gitee / origin 推送规则

### 8.1 推送顺序

**Gitee 必须先推送**，因为远端服务器从 Gitee 拉取。

```powershell
git push gitee feature/<branch>
git push origin feature/<branch>
```

### 8.2 推送检查

```powershell
git remote -v
git branch --show-current
git log -1 --oneline
```

### 8.3 推送失败处理

| 错误 | 处理 |
| --- | --- |
| `non-fast-forward` | 先 `git pull --ff-only`，确认无冲突后重试 |
| `permission denied` | 停止推送，联系维护者更新 SSH key / token |
| `repository not found` | 停止推送，确认 remote URL 是否正确 |
| `large file` 警告 | 确认文件是否应纳入仓库；如不应，加入 `.gitignore` |
| `pre-receive hook declined` | 停止推送，根据 hook 提示修复 |

## 9. 部署前 commit 对齐规则

### 9.1 本地

```powershell
git rev-parse HEAD
```

记录 commit hash 到 report。

### 9.2 远端

```bash
cd /opt/saas/app
git fetch gitee feature/auth-system
git checkout feature/auth-system
git pull --ff-only gitee feature/auth-system
git rev-parse HEAD
```

### 9.3 对齐验证

- 本地 HEAD == 远端 HEAD
- 远端 `git status --short` 为空
- 远端 jar / dist 时间与 commit 时间一致

### 9.4 部署前禁止

- 禁止使用 `git reset --hard` 在远端重写历史（除非所有 remote 都已同步）。
- 禁止 `git push --force`。
- 禁止在远端直接 `git checkout <dirty-files>`。

## 10. 报告模板

每个 GIT-BATCH-N 必须生成报告 `harness/reports/git-batch-<N>-<scope>-<purpose>-<timestamp>.md`，包含：

1. 任务概述（编号 / 名称 / 时间 / HEAD）
2. 初始 dirty 状态
3. 文件分类表（每文件：路径、状态、分类、所属任务、所属 Batch、stage / 提交 / 部署）
4. staged 文件清单
5. 审查结论（9 项审查点）
6. commit hash + 推送结果
7. 远端部署过程（仅 runtime 批次）
8. 远端 commit 对齐验证
9. 关键不变量确认
10. 残留 dirty 状态
11. Verification Summary
12. 下一步建议
13. 报告路径

## 11. 与其他文件的关系

- `harness/skills/git-change-control.md`：定义全部 Git Gate。
- `harness/SESSION_EXIT_GATE.md`：会话退出前 Git 状态。
- `harness/COMPLETION_GATES.md`：完成门禁的 Git 子门禁。
- `harness/FORBIDDEN_SCOPE.md`：禁止项。
- `harness/TASK_ROUTING.md`：任务路由。
