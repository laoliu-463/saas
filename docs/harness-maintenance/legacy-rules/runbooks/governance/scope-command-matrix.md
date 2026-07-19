# Runbook：Scope → Command Matrix

## 主入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope <SCOPE> -ReportKey <key> -OwnedFiles '<path1>;<path2>' -Message "<msg>" [-DryRun]
```

`-DeployRemote true` 已停用；主入口只负责本地验证、证据、提交和推送。

## Scope 决策

| Scope | 适用 | 必跑 | 限制 |
| --- | --- | --- | --- |
| `docs` | 文档、Harness、报告 | safety、结构门禁、稳定 evidence | 构建/重启/健康明确跳过 |
| `backend` | Java、SQL、迁移 | Maven 测试/打包、本地后端重启、健康、业务 smoke | 不部署远端 |
| `frontend` | Vue/Vite/页面 | 测试、typecheck、build、本地前端重启、页面 smoke | 不部署远端 |
| `full` | 跨前后端/数据库 | backend + frontend + 本地 Compose + 业务验证 | 不部署远端 |
| `diagnosis` | 只读排查 | 图谱、日志、API/SQL 证据 | 不实现、不部署 |
| 远端发布候选 | 用户要求发布 | `full` 验证、提交、推送、PR/候选 SHA | 等待 Merge Queue/Jenkins |

## 环境决策

| 环境 | 何时使用 |
| --- | --- |
| 本地 `real-pre` | 默认工程修改和验证环境 |
| `test` | 用户明确要求或专项 mock / 回归 |
| 远端 `real-pre` | 仅 Jenkins；普通任务没有 SSH、部署或回滚入口 |

## 发布决策

| 触发 | 普通任务行为 |
| --- | --- |
| 用户没提发布 | 只做本地验证 |
| 用户要求发布 | 生成候选 SHA 与证据，进入 PR/Merge Queue |
| Jenkins 成功 | 记录完整 SHA/digest、五项版本一致和发布清单 |
| Jenkins 失败/阻塞 | 如实记录 FAIL/BLOCKED，不改用手工部署 |
| 用户要求回滚 | 进入同一 Jenkins 队列，需 `ROLLBACK_APPROVED=true` |

## 禁止矩阵

| 禁止 | 范围 |
| --- | --- |
<<<<<<< HEAD:docs/harness-maintenance/legacy-rules/runbooks/governance/scope-command-matrix.md
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
=======
| `git add .`、提交密钥、改真实 `.env*` | 全部 |
| SSH 修改服务器、`/opt/saas/app` 现场构建 | 普通任务 |
| `deploy-remote.ps1`、`deploy-real-pre.sh`、`rollback-real-pre.sh` | 全部普通任务 |
| 短 SHA、`latest`、可变发布 tag | 远端发布 |
| 并行合并、并行迁移、并行部署/E2E | 远端流程 |
| 清库、`docker compose down -v` | 全部 |

发布详情见 `harness/rules/cicd-real-pre-policy.md`。
>>>>>>> b8cb837b (refactor: establish real-pre single-channel CD):harness/rules/runbooks/governance/scope-command-matrix.md
