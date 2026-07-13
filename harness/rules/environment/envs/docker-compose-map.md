# Docker Compose Map

| 环境 | Compose | Project | Backend | Frontend | PostgreSQL | Redis | 端口 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `test` | `docker-compose.test.yml` | `saas-test` | `backend` | `frontend` | `postgres` | `redis` | `8080 / 3000` |
| `real-pre` | `docker-compose.real-pre.yml` | `saas-active` | `backend-real-pre` | `frontend-real-pre` | `postgres-real-pre` | `redis-real-pre` | `8081 / 3001` |

## 健康检查

- test 后端：`http://127.0.0.1:8080/api/system/health`。
- real-pre 后端：`http://127.0.0.1:8081/api/system/health`。
- test 前端：`http://127.0.0.1:3000/healthz` 或 `/`。
- real-pre 前端：`http://127.0.0.1:3001/healthz`。

## 禁止删除的 volume

- `postgres_test_data`
- `redis_test_data`
- `saas-active_postgres_real_pre_data`
- `saas-active_redis_real_pre_data`

## 操作入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope full
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope full
```

禁止 `docker compose down -v`、删除 volume 或临时更改 project name 创建第二套 real-pre 数据卷。
