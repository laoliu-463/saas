# real-pre 环境说明

## 定位

`real-pre` 是真实联调环境，用于真实抖店上游、真实 Token、真实商品、真实订单和上线前受控验证。

real-pre 不是 test/mock 基线，不能用 mock 数据证明真实业务闭环。

## 配置

| 项 | 当前事实 |
| --- | --- |
| Compose | `docker-compose.real-pre.yml` |
| Env | `.env.real-pre` |
| Project | `saas-active` |
| 后端服务 | `backend-real-pre` |
| 前端服务 | `frontend-real-pre` |
| PostgreSQL 服务 | `postgres-real-pre` |
| Redis 服务 | `redis-real-pre` |
| 后端端口 | `8081` |
| 前端端口 | `3001` |
| 后端健康 | `http://127.0.0.1:8081/api/system/health` |
| 前端健康 | `http://127.0.0.1:3001/healthz` 或 `/login` |

## 必须保持

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`

## 禁止事项

- 禁止 mock。
- 禁止清库。
- 禁止 `docker compose down -v`。
- 禁止删除 PostgreSQL / Redis volume。
- 禁止用 test 结果证明 real-pre 闭环。
- 禁止关闭真实抖音 API 开关后声明真实闭环通过。

## 订单归因验证要求

真实闭环必须检查：

- `orders.pick_source`
- `orders.default_channel_id`
- `orders.default_recruiter_id`
- `sample_requests` 状态流转
- `performance_records`
- dashboard / 业绩归属

## 常用命令

```powershell
npm run start:real-pre
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

## 结论口径

- 无失败但无真实订单：环境可用，P0 仍因样本不足保持 `PENDING`。
- 有真实订单且归因、寄样、业绩通过：real-pre P0 可升级。
- 出现失败：按失败项定级修复或回滚，不得放量。

