# 构建重启部署命令规划

## 当前已有脚本

| 脚本 | 当前路径 | 当前职责 | 状态 |
| --- | --- | --- | --- |
| `agent-do.ps1` | `harness/commands/agent-do.ps1` | 串联安全检查、构建、重启、健康检查、业务验证、证据、Git、远端部署、复盘 | 已存在 |
| `safety-check.ps1` | `harness/commands/safety-check.ps1` | 敏感文件、real-pre 开关、危险命令引用检查 | 已存在 |
| `restart-compose.ps1` | `harness/commands/restart-compose.ps1` | 按 env/scope 执行 `docker compose up -d --build` | 已存在 |
| `verify-local.ps1` | `harness/commands/verify-local.ps1` | 本地 HTTP 健康检查 | 已存在 |
| `collect-evidence.ps1` | `harness/commands/collect-evidence.ps1` | 生成 evidence report | 已存在 |
| `retire-content.ps1` | `harness/commands/retire-content.ps1` | 生成旧内容维护计划，按 manifest 归档或删除旧内容 | 已存在 |
| `deploy-remote.ps1` | `harness/commands/deploy-remote.ps1` | 远端 real-pre 拉取、重建、健康检查 | 已存在 |
| `new-retro.ps1` | `harness/commands/new-retro.ps1` | 生成任务后复盘 | 已存在 |
| `git-push-safe.ps1` | `harness/commands/git-push-safe.ps1` | 敏感文件检查、提交、推送 | 已存在 |

## 未来 agent-do.ps1 应继续做什么

- 根据 `Env` 和 `Scope` 选择构建、重启、健康检查与业务验证。
- 默认使用本地 `real-pre`；`test` 必须显式指定。
- 执行顺序必须保持为构建 -> 重启 -> 健康检查 -> 业务验证，避免业务验证失败时跳过重启和健康证据。
- docs-only 场景跳过构建、重启和业务 E2E，但保留安全检查、证据和复盘。
- real-pre 场景强制检查真实开关。
- 不把 `PARTIAL`、`PENDING` 或 `BLOCKED` 写成 `PASS`。

## 未来 safety-check.ps1 应继续做什么

- 检查 `.env*`、私钥、证书、Token 等敏感变更。
- 检查 real-pre 必须为真实模式。
- 扫描 `scripts/` 和 `harness/` 的危险命令引用。
- 对 `docker compose down -v`、volume 删除、清库语句保持阻断。

## 未来 restart-compose.ps1 应继续做什么

- 仅使用 `docker compose up -d --build` 重建指定服务。
- 不执行 `down -v`。
- 按 scope 只重启 backend、frontend 或 full。
- 输出 compose ps 作为证据。

## 未来 verify-local.ps1 应继续做什么

- 根据 compose 和 env 推导端口。
- 后端验证 `/api/system/health` 且 `status=UP`。
- 前端尝试 `/healthz`、`/login` 或 `/`。
- docs-only 只做结构检查。

## 未来 deploy-remote.ps1 应继续做什么

- 只在用户明确要求远端部署时执行。
- 默认 Host：`saas`。
- 默认代码目录：`/opt/saas/app`。
- 默认 Env：`/opt/saas/env/.env.real-pre`。
- 执行远端 `git pull --ff-only`、Compose 重建、docker ps、后端健康和前端健康。
- 禁止远端清库、删除 volume 或切成 mock。

## 未来 retire-content.ps1 应继续做什么

- 每次任务后默认生成旧内容维护候选报告。
- 归档 / 删除必须使用 manifest。
- 阻断 env、密钥、compose、数据库 migration、Git 元数据等受保护路径。
- 源码类路径必须显式 `-AllowSourceCode`，且要完成对应构建和业务验证。
- 删除目录必须在 manifest 中显式 `allowRecursive=true`。

## 待建设

- 更细分的业务验证参数，例如按领域自动选择订单归因、寄样、商品库、看板 eval。
- evidence report 中自动记录本次新旧文档冲突。
- 无关 dirty worktree 时的安全提交流程，避免误提交用户变更。
