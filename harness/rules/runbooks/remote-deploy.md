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
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app
```

或由总入口触发：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -DeployRemote true -Message "说明本次部署"
```

## 固定远端流程

1. `ssh` 进入远端目录。
2. `git pull --ff-only`。
3. `docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre`。
4. `docker compose ps`。
5. `curl http://127.0.0.1:8081/api/system/health`。
6. `curl http://127.0.0.1:3001/healthz`。

## 部署前检查（同步参数）

部署前必须确认远端 env 文件包含同步相关参数：

```bash
ssh saas "grep PRODUCT_ACTIVITY_SYNC /opt/saas/env/.env.real-pre"
ssh saas "cd /opt/saas/app && docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml config | grep PRODUCT_ACTIVITY"
```

期望输出：

```env
PRODUCT_ACTIVITY_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
```

如果 env 文件缺失，必须在部署前补充到远端 env 文件。compose 与 real-pre profile 仍提供默认值，但远端受控部署必须显式记录 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 5 分钟周期，避免环境漂移。

远端启用前必须确认 `P-FIX-002B` 已修复 `uk_pos_one_displaying_per_product` 唯一索引冲突；否则 5 分钟同步会放大失败频率。

## 部署后检查（同步任务）

部署后必须验证同步任务配置已加载：

```bash
ssh saas "docker exec backend-real-pre printenv | grep PRODUCT_ACTIVITY"
ssh saas "docker logs --tail=200 backend-real-pre 2>&1 | grep ProductActivitySyncJob"
ssh saas "curl -s http://127.0.0.1:8081/api/system/health"
ssh saas "curl -s http://127.0.0.1:3001/healthz"
```

期望日志：

```
ProductActivitySyncJob config: enabled=true, cron=0 */5 * * * ?, batchSize=20, whitelist=(all active)
```

远端启用后必须复核商品库数量，至少对账 `product_snapshot`、`product_operation_state`、`DISPLAYING` 数量和商品库 API total。

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
