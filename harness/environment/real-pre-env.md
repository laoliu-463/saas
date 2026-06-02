# Real-pre Environment

## 用途

`real-pre` 是真实联调环境，用于真实抖店上游、真实 Token、真实商品、真实订单和上线前受控验证。

## 配置

- Compose：`docker-compose.real-pre.yml`
- Env：`.env.real-pre`
- 前端默认端口：`3001`
- 后端默认端口：`8081`
- 后端健康：`http://127.0.0.1:8081/api/system/health`
- 前端健康：`http://127.0.0.1:3001/healthz`

## 必须保持

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`

## 禁止事项

- 禁止 mock。
- 禁止清库。
- 禁止 `docker compose down -v`。
- 禁止删除 volume。
- 禁止用 test 结果证明 real-pre 闭环。

## 真实订单归因验证

真实闭环必须检查：

- `orders.pick_source`
- `orders.default_channel_id`
- `orders.default_recruiter_id`
- `sample_requests` 状态流转
- `performance_records`
- dashboard / 业绩归属

