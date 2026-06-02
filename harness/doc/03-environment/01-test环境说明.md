# test 环境说明

## 定位

`test` 是本地 mock / seed 回归环境，可用于基础功能验证、权限回归、P0 浏览器验收、后端构建和前端构建。

test 不能证明 real-pre 真实闭环。

## 配置

| 项 | 当前事实 |
| --- | --- |
| Compose | `docker-compose.test.yml` |
| Env | `.env.test` |
| Project | `saas-test` |
| 后端服务 | `backend` |
| 前端服务 | `frontend` |
| PostgreSQL 服务 | `postgres` |
| Redis 服务 | `redis` |
| 后端端口 | `8080` |
| 前端端口 | `3000` |
| 后端健康 | `http://127.0.0.1:8080/api/system/health` |

## 允许事项

- 允许使用 mock / seed 数据。
- 允许执行 P0 回归和浏览器 E2E。
- 允许重启 test 容器。
- 允许在测试库中准备验证数据。

## 禁止事项

- 禁止用 test 结果证明 real-pre 真实闭环。
- 禁止用 mock 订单证明真实渠道归因。
- 禁止把 test 开关复制到 real-pre。

## 常用命令

```powershell
npm run start:test
npm run e2e:v1-p0
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope full -Message "fix: test change"
```

## 证据

- Playwright 报告：`playwright-report/`
- QA 输出：`runtime/qa/out/`
- Harness 报告：`harness/reports/evidence-*.md`

