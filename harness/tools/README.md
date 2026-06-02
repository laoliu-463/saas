# Tools System

## 目标

Tools 子系统提供固定脚本，让 AI Agent 不再临时猜构建、重启、验证、部署和报告流程。

## 固定入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope full -Message "说明本次修改"
```

## 命令清单

| 脚本 | 作用 |
| --- | --- |
| `safety-check.ps1` | 执行前安全检查 |
| `restart-compose.ps1` | 按环境和 scope 重启 Docker Compose 服务 |
| `verify-local.ps1` | 本地健康检查 |
| `collect-evidence.ps1` | 生成 evidence report |
| `git-push-safe.ps1` | 敏感文件检查、commit、push |
| `deploy-remote.ps1` | 远端 real-pre 部署 |
| `new-retro.ps1` | 生成 Harness retro summary |
| `agent-do.ps1` | 总入口，串联全部步骤 |

## Scope

| Scope | 行为 |
| --- | --- |
| `docs` | 跳过构建、重启和业务 E2E；执行安全检查、证据、复盘 |
| `backend` | 后端构建、重启 backend、后端健康 |
| `frontend` | 前端构建、重启 frontend、前端健康 |
| `full` | 后端 + 前端完整链路 |

## DryRun

所有 Harness 命令必须支持 `-DryRun`。DryRun 只打印计划或模拟报告，不执行破坏性动作、不部署远端、不提交推送。

