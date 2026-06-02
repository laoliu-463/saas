# Remote Real-pre Environment

## 仓库事实

当前部署文档记录的远端入口：

- SSH Host：`saas`
- Repo dir：`/opt/saas/app`
- Env file：`/opt/saas/env/.env.real-pre`
- Compose：`docker-compose.real-pre.yml`
- 后端健康：`http://127.0.0.1:8081/api/system/health`
- 前端健康：`http://127.0.0.1:3001/healthz`

用户默认模板中写 `RemoteDir=/opt/saas`；当前仓库事实是代码目录 `/opt/saas/app`。脚本默认采用仓库事实，可通过参数覆盖。

## 部署入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app
```

## 回滚

远端回滚见 `harness/runbooks/rollback.md`。real-pre 回滚禁止清库和删除 volume。

## 待确认项

- 远端分支是否仍为 `feature/auth-system`。
- 远端是否每次部署后强制执行完整 P0 / roles。
- 远端真实写入开关是否有临时冻结窗口。

