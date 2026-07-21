# Deployment State

> **主源说明**：远端部署以 `docs/harness-maintenance/legacy-rules/environment/envs/remote-real-pre-env.md` 为准；
> 命令决策表以 `docs/harness-maintenance/legacy-rules/runbooks/governance/scope-command-matrix.md` 为准。冲突应登记到 `docs/harness-maintenance/legacy-rules/changelog.md`。

## 当前部署口径

| 环境 | 状态 | 证据主源 | 说明 |
| --- | --- | --- | --- |
| test | 可用于 mock / P0 回归 | `docs/harness-maintenance/legacy-rules/environment/envs/test-env.md`、`docker-compose.test.yml` | 仅在显式要求或专项测试中使用 |
| 本地 real-pre | 默认工程修改环境 | `docs/harness-maintenance/legacy-rules/environment/envs/real-pre-env.md`、`docker-compose.real-pre.yml` | 前端 `3001`，后端 `8081` |
| 远端 real-pre | 只允许 Jenkins 串行发布 | `docs/harness-maintenance/legacy-rules/cicd-real-pre-policy.md`、`docs/10-部署运行总览.md` | 唯一来源 `release/real-pre`，不等于正式全量上线 |

## 当前限制

- real-pre P0 不能因环境健康而直接判定全量通过；仍受真实订单 / `pick_source` 样本影响。
- 远端部署必须进入 Jenkins `saas-real-pre-cd` 唯一队列，不能由 Agent 或 SSH 手写流程绕过。
- `.env.real-pre` 不进入 Git，也不复制到文档。

## 远端发布触发条件

1. 候选已通过 PR/CI 串行进入 `main`。
2. 发布提升 PR 将候选进入 `release/real-pre`。
3. 发布人确认 Jenkins 参数 `DEPLOY_REAL_PRE=true`；真实推广写开启时同时确认对应参数。
4. Jenkins 顺序、镜像、数据库 diff、运行版本和业务验证门禁全部通过。

`agent-do.ps1 -DeployRemote true` 与 `deploy-remote.ps1` 直接部署入口已停用。
