# Docker Compose Map

| 环境 | Compose | Project | Backend | Frontend | PostgreSQL | Redis | 端口 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `test` | `docker-compose.test.yml` | `saas-test` | `backend` | `frontend` | `postgres` | `redis` | `8080 / 3000` |
| `real-pre` | `docker-compose.real-pre.yml` | `saas-active` | `backend-real-pre` | `frontend-real-pre` | `postgres-real-pre` | `redis-real-pre` | `8081 / 3001` |

## 禁止删除的 volume

- `postgres_test_data`
- `redis_test_data`
- `saas-active_postgres_real_pre_data`
- `saas-active_redis_real_pre_data`

## 健康检查

- 后端：`/api/system/health` 返回 `status=UP`。
- test 前端：`/favicon.svg` 或 `/`。
- real-pre 前端：`/healthz` 或 `/login`。

## 操作入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope full
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope full
```

