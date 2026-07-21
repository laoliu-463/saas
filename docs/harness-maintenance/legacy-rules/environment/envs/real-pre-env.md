# Real-pre Environment

`real-pre` 是真实上游联调环境，用于真实 Token、商品和订单的受控验证；不能使用 mock 结果证明闭环。

## 配置

| 项 | 当前事实 |
| --- | --- |
| Compose | `docker-compose.real-pre.yml` |
| Env | `.env.real-pre` |
| Project | `saas-active` |
| 后端 / 前端 | `backend-real-pre` / `frontend-real-pre` |
| PostgreSQL / Redis | `postgres-real-pre` / `redis-real-pre` |
| 后端 / 前端端口 | `8081` / `3001` |
| 后端健康 | `http://127.0.0.1:8081/api/system/health` |
| 前端健康 | `http://127.0.0.1:3001/healthz` |

## 必须保持

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`

## 禁止事项

- 禁止 mock、清库和删除 volume。
- 禁止 `docker compose down -v`。
- 禁止关闭真实上游后声明真实闭环通过。

## 闭环证据

必须继续验证 `orders.pick_source`、默认渠道/招商归属、寄样状态、`performance_records` 和看板归属。环境健康不等于业务闭环通过。
