# Runbook: Scope → Command Matrix

> 单一权威表：每种 scope 走哪条命令，必须输出什么，禁止什么。
> 取代在 `TASK_ROUTING.md` 表格中分散的命令段落。

## 1. 主入口（90% 场景）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope <SCOPE> -ReportKey <key> -OwnedFiles '<path1>;<path2>' -Message "<msg>" [-DeployRemote true] [-DryRun]
```

`<SCOPE>` 决定后续自动调用哪些子命令；`ReportKey` 决定稳定 evidence 路径，`OwnedFiles` 限定任务可暂存的文件。

## 2. Scope 决策表

| Scope | 适用 | 必跑命令 | 可选命令 | 跳过 | 失败处理 |
| --- | --- | --- | --- | --- | --- |
| **docs** | 只改 harness / docs / 报告 / 状态 | `safety-check.ps1 -Env real-pre -Scope docs -DryRun` + `verify-local.ps1 -Scope docs` + `git diff --check` | `retire-content.ps1 -Action Plan` | build / restart / health / E2E | 升级到 backend / frontend / full |
| **backend** | 改 Java / Mapper / Service / Controller / SQL / migration | `mvn -f backend/pom.xml test` + `mvn -f backend/pom.xml -DskipTests package` + `safety-check.ps1 -Scope backend` + `restart-compose.ps1 -Scope backend` + `curl /api/system/health` + 一条相关 API smoke | 只读 SQL 对账 | 前端构建 | E2E 业务验证缺失 → 升级 full |
| **frontend** | 改 Vue / Vite / Pinia / 路由 / API 调用 | `npm --prefix frontend run build` + `npm --prefix frontend run test` + `safety-check.ps1 -Scope frontend` + `curl /healthz` + 页面 smoke | `playwright` 关键页 | 后端 build | 后端联动缺失 → 升级 full |
| **full** | 同时改前后端 / SQL / 跨域 | backend 必跑 + frontend 必跑 + `restart-compose.ps1` + backend health + frontend healthz + 业务 smoke | E2E（real-pre） | 无 | 必跑稳定 evidence；状态变化时更新 CURRENT_STATE |
| **deploy** | 部署远端 | `git fetch` + `git checkout` + `git pull --ff-only` + 远端 `git rev-parse HEAD` 对齐 + `restart-compose.ps1 -Scope full` + 远端 health + 远端 `docker compose ps` | 远端 `npm run e2e:real-pre:p0:preflight` | 无 | 立即生成 rollback 报告，远端 `git revert` |
| **diagnosis** | 排查问题，先不改 | `code-review-graph` 工具 + `rg` + API / SQL / 日志取证 | 复现脚本 | 无 | 阶段性结论（未修） |

## 3. Env 决策

| Env | 何时使用 |
| --- | --- |
| `real-pre` | **默认**。本地 `real-pre` 容器；前端 `3001`、后端 `8081`、PostgreSQL、Redis。 |
| `test` | 用户明确要求或专项 mock / 回归（`npm run e2e:v1-p0`）。 |
| 远端 | 部署任务用 `-DeployRemote true`；不能单独 `-Env remote`。 |

## 4. Deploy 决策

| 触发 | 行为 |
| --- | --- |
| 用户明确说"部署" | 加 `-DeployRemote true` |
| 用户说"本地测" | **不要**加 `-DeployRemote true` |
| 用户没提部署 | 保持本地验证，不主动部署 |

## 5. 必读文件（按 scope）

| Scope | 必读 |
| --- | --- |
| docs | `AGENTS.md`、`CLAUDE.md`、`docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md`、`docs/harness-maintenance/legacy-rules/governance/task-routing.md`、`docs/harness-maintenance/legacy-rules/governance/forbidden-scope.md` |
| backend | docs + `docs/harness-maintenance/legacy-rules/instructions/domain/<domain>.md` + 对应 `docs/领域/*.md` + `docs/05-API契约总表.md` + `docs/06-数据模型总表.md` |
| frontend | docs + `docs/harness-maintenance/legacy-rules/skills/workflow/frontend-ux.skill.md` + 对应 API |
| full | backend + frontend 全套 |
| deploy | `docs/harness-maintenance/legacy-rules/environment/envs/remote-real-pre-env.md` + `docs/harness-maintenance/legacy-rules/runbooks/remote-deploy.md` |
| diagnosis | `docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md` + `docs/harness-maintenance/legacy-rules/state/snapshots/KNOWN_ISSUES.md` + 对应 domain 文档 |

## 6. 验证命令最小集

```powershell
# docs
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope docs
git diff --check

# backend
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope backend
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend
curl http://localhost:8081/api/system/health

# frontend
npm --prefix frontend run build
npm --prefix frontend run test
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope frontend
curl http://localhost:3001/healthz
```

## 7. 禁止矩阵

| 禁止 | 适用 scope |
| --- | --- |
| 修改 `backend/src/main/` | docs / harness-only |
| 修改 `frontend/src/` | docs / harness-only / backend-only |
| 修改 SQL migration | docs / harness-only |
| 重启容器 | docs / harness-only |
| `git add .` | 全部 |
| 远端部署 | 未明确要求时 |
| 修改 `.env*` 真实文件 | 全部 |
| 清库 / `docker compose down -v` | 全部 |

## 8. 关联文档

- `harness/scripts/commands/*.ps1`
- `docs/harness-maintenance/legacy-rules/governance/task-routing.md`
- `docs/harness-maintenance/legacy-rules/governance/COMPLETION_GATES.md`
- `docs/harness-maintenance/legacy-rules/runbooks/governance/task-lifecycle.md`
- `docs/harness-maintenance/legacy-rules/environment/CHEATSHEET.md`
