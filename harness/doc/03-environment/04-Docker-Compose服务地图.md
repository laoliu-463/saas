# Docker Compose 服务地图

## Compose 文件

| 环境 | Compose | Project | Backend | Frontend | PostgreSQL | Redis | 端口 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `test` | `docker-compose.test.yml` | `saas-test` | `backend` | `frontend` | `postgres` | `redis` | `8080 / 3000` |
| `real-pre` | `docker-compose.real-pre.yml` | `saas-active` | `backend-real-pre` | `frontend-real-pre` | `postgres-real-pre` | `redis-real-pre` | `8081 / 3001` |

## test 服务

| 服务 | 端口 | 健康检查 |
| --- | --- | --- |
| `postgres` | `5432:5432` | `pg_isready` |
| `redis` | `6379:6379` | `redis-cli ping` |
| `backend` | `8080:8080` | `http://127.0.0.1:8080/api/system/health` |
| `frontend` | `3000:3000` | `http://127.0.0.1:3000/favicon.svg` 或 `/healthz` |

## real-pre 服务

| 服务 | 端口 | 健康检查 |
| --- | --- | --- |
| `postgres-real-pre` | Compose 内部 `5432` | `pg_isready` |
| `redis-real-pre` | Compose 内部 `6379` | `redis-cli ping` |
| `backend-real-pre` | `${BACKEND_HOST_PORT:-8081}:8080` | `http://127.0.0.1:8081/api/system/health` |
| `frontend-real-pre` | `${FRONTEND_HOST_PORT:-3001}:80` | `http://127.0.0.1:3001/healthz` |

## 禁止删除的 volume

- `postgres_test_data`
- `redis_test_data`
- `saas-active_postgres_real_pre_data`
- `saas-active_redis_real_pre_data`

## 操作入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope full
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope full
```

`test` Compose 仅作为显式专项环境使用，不能作为默认工程修改目标。

## 安全规则

- 不使用 `docker compose down -v`。
- 不删除 PostgreSQL / Redis volume。
- 不临时改 project name 导致创建第二套 real-pre 数据卷。
- real-pre 必须保持真实模式开关。
