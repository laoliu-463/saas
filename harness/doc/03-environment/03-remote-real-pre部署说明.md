# remote real-pre 部署说明

## 当前仓库事实

| 项 | 当前值 | 说明 |
| --- | --- | --- |
| SSH Host | `saas` | 已在现有部署文档中记录，仍需在每次部署前确认可连通。 |
| RemoteDir | `/opt/saas/app` | 当前代码目录；提示词默认 `/opt/saas` 只作为待确认模板，不覆盖仓库事实。 |
| Env file | `/opt/saas/env/.env.real-pre` | 不提交到 Git。 |
| ComposeFile | `docker-compose.real-pre.yml` | 远端复用 real-pre compose。 |
| 后端健康 | `http://127.0.0.1:8081/api/system/health` | 公开健康探针。 |
| 前端健康 | `http://127.0.0.1:3001/healthz` | 或 `/login`。 |

## 部署前提

- 用户明确要求远端部署。
- 本地修改已安全提交并推送，远端可 `git pull --ff-only` 获取。
- real-pre env 文件在远端已正确配置。
- 不执行清库、不删除 volume、不改成 mock。

## 固定入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app
```

## 远端流程

```bash
cd /opt/saas/app
git pull --ff-only
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml ps
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

## 回滚注意事项

- 回滚见 `harness/runbooks/rollback.md`。
- real-pre 回滚禁止清库和删除 volume。
- 只能回滚代码 / 镜像 / 配置，不能用清空数据证明恢复。

## 待确认项

- 远端分支是否仍为 `feature/auth-system`。
- 是否每次远端部署后都强制执行完整 `e2e:real-pre:p0` 和 `roles`。
- 真实推广写入开关是否存在临时冻结窗口。

