# Test Environment

`test` 是本地 mock / seed 回归环境，不能证明 real-pre 真实闭环。

## 配置

| 项 | 当前事实 |
| --- | --- |
| Compose | `docker-compose.test.yml` |
| Env | `.env.test` |
| Project | `saas-test` |
| 后端 / 前端 | `backend` / `frontend` |
| PostgreSQL / Redis | `postgres` / `redis` |
| 后端 / 前端端口 | `8080` / `3000` |
| 后端健康 | `http://127.0.0.1:8080/api/system/health` |

## 允许与禁止

- 允许 mock、seed、P0 回归和浏览器 E2E。
- 允许在测试库准备验证数据。
- 禁止用 mock 订单证明真实渠道归因。
- 禁止把 test 开关复制到 real-pre。

## 常用命令

```powershell
npm run start:test
npm run e2e:v1-p0
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env test -Scope full -Message "test: regression"
```
