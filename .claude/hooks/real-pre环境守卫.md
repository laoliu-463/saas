# real-pre 环境守卫

[V1 必做] real-pre 只用于真实 SDK / 真实抖店上游联调，不能使用 mock 数据冒充真实闭环。

[V1 必做] 操作前检查：

| 检查项 | 期望 |
| --- | --- |
| 前端 | `http://localhost:3001` |
| 后端 | `http://localhost:8081/api` |
| `/api/system/env` | `environmentLabel=REAL-PRE` |
| `APP_TEST_ENABLED` | `false` |
| `DOUYIN_TEST_ENABLED` | `false` |
| 数据库 | `saas_real_pre` |

[V1 必做] 统一入口为 `npm run e2e:real-pre:p0`。细分脚本不能替代上线前统一验收。

[V1 必做] 缺 Token、缺授权、缺真实订单或缺可复用 `pick_source_mapping` 时，结论只能是 `BLOCKED` 或 `PENDING`。

