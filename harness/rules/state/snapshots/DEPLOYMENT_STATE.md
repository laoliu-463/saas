# Deployment State

> **主源说明**：远端部署以 `harness/rules/environment/envs/remote-real-pre-env.md` 为准；
> 命令决策表以 `harness/rules/runbooks/governance/scope-command-matrix.md` 为准。冲突应登记到 `harness/rules/changelog.md`。

## 当前部署口径

| 环境 | 状态 | 证据主源 | 说明 |
| --- | --- | --- | --- |
| test | 可用于 mock / P0 回归 | `harness/rules/environment/envs/test-env.md`、`docker-compose.test.yml` | 仅在显式要求或专项测试中使用 |
| 本地 real-pre | 默认工程修改环境 | `harness/rules/environment/envs/real-pre-env.md`、`docker-compose.real-pre.yml` | 前端 `3001`，后端 `8081` |
| 远端 real-pre | 允许受控部署，但需用户明确要求 | `harness/rules/environment/envs/remote-real-pre-env.md`、`docs/10-部署运行总览.md` | 不等于正式全量上线 |

## 最新受控部署（2026-07-15）

- `feature/auth-system` 的远端仓库 HEAD `66ddf8bd`（业务代码含 `82456081` 与既有 RBAC 集成链）已通过固定脚本部署到远端 `real-pre`；backend、frontend、PostgreSQL、Redis 均健康。
- 本轮部署前 PostgreSQL dump 为 `/opt/saas/backups/rbac-redeploy-pre-20260715-131037.dump`；重启后仍为 4 张授权表、1 个 `authz_version` 列、0 条授权事实。
- Authorization 保持 `LEGACY(default)`，未进入 `SHADOW/ENFORCE`。
- 部署技术门禁通过，但业务门禁为 `PARTIAL`：P0 与六角色完整业务流均为 `FAIL`，不得据此声明正式全量上线。最新证据：`harness/reports/current/latest-rbac-phase2-remote-redeploy.md`。

## 当前限制

- real-pre P0 不能因环境健康而直接判定全量通过；仍受真实订单 / `pick_source` 样本影响。
- 远端部署必须使用固定入口，不能临时手写部署流程。
- `.env.real-pre` 不进入 Git，也不复制到文档。

## 远端部署触发条件

只有用户明确要求远端部署时，才允许执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -DeployRemote true -Message "deploy: real-pre update"
```
