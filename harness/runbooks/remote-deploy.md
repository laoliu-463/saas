# Runbook: remote deploy

## 适用场景

用户明确要求部署远端 real-pre 服务器时使用。

## 默认参数

- SSH alias：`saas`
- 远端目录：`/opt/saas/app`
- Compose 文件：`docker-compose.real-pre.yml`
- 远端 env：`/opt/saas/env/.env.real-pre`

## 执行入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app
```

或由总入口触发：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote true -Message "说明本次部署"
```

## 固定远端流程

1. `ssh` 进入远端目录。
2. `git pull --ff-only`。
3. `docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre`。
4. `docker compose ps`。
5. `curl http://127.0.0.1:8081/api/system/health`。
6. `curl http://127.0.0.1:3001/healthz`。

## 禁止事项

- 不执行 `down -v`。
- 不删除 PostgreSQL / Redis volume。
- 不把本机 `.env.real-pre` 复制到远端。
- 不输出密钥或 Token。

## 常见失败处理

| 失败 | 处理 |
| --- | --- |
| SSH 不通 | 检查 `RemoteHost`、网络和密钥，不猜测密码 |
| `git pull` 失败 | 检查远端工作区和分支，不强制 reset |
| compose 构建失败 | 保留构建日志，标记 `FAIL` |
| 健康检查失败 | 查容器日志和 env guard，标记 `FAIL` 或 `BLOCKED` |

