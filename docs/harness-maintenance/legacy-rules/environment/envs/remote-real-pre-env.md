# Remote Real-pre Environment

## 当前事实

| 项 | 当前值 |
| --- | --- |
| SSH Host | `saas` |
| Repo dir | `/opt/saas/app` |
| Env file | `/opt/saas/env/.env.real-pre` |
| Compose | `docker-compose.real-pre.yml` |
| 后端健康 | `http://127.0.0.1:8081/api/system/health` |
| 前端健康 | `http://127.0.0.1:3001/healthz` |

## 部署前提

- 用户明确要求远端部署。
- 本地修改已按任务范围提交和推送。
- 远端工作区干净并与目标 commit 对齐。
- 不清库、不删除 volume、不切换 mock。

## 固定入口

```powershell
远端发布不通过 `agent-do.ps1` 或 SSH 执行；完成 `main` 合并和 `release/real-pre.json` 提升 PR 后，由 Jenkins `saas-real-pre-cd` 消费发布清单。
```

回滚规则见 `../../runbooks/rollback.md`。远端分支、冻结窗口和完整 E2E 要求必须在每次部署前重新取证，不在本文件写成固定事实。
