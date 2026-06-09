# Evidence Report - dashboard today metric reconcile

- 时间：2026-06-09 12:55:00 CST
- 环境：real-pre
- 分支：feature/auth-system
- commit hash：7f72e51c
- 主报告：`harness/reports/dashboard-today-metric-reconcile-20260609-125500.md`
- 工作区状态：任务开始前已存在多份未跟踪 `harness/reports/*` 报告；本轮新增本 evidence report 和主审查报告，修改两个测试文件。

## 构建 / 测试

后端 targeted tests：

```text
mvn -f backend/pom.xml "-Dtest=PerformanceMetricsQueryServiceTest,PerformanceCalculationServiceTest,DataControllerTest" test
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

前端 targeted tests：

```text
npm --prefix frontend run test -- src/views/data/index.test.ts src/views/data/dashboard-metrics.test.ts src/api/data.test.ts
Test Files: 3 passed
Tests: 9 passed
```

## Docker / 健康检查

- `saas-active-frontend-real-pre-1`：healthy
- `saas-active-backend-real-pre-1`：healthy
- `saas-active-postgres-real-pre-1`：healthy
- `saas-active-redis-real-pre-1`：healthy
- 前端 `/healthz`：`ok`
- 后端 `/actuator/health`：404
- 后端 `/api/actuator/health`：401，需要 Authorization

## 业务验证

- real-pre PostgreSQL 只读对账已执行。
- 对账事务：`REPEATABLE READ READ ONLY`
- DB 时间：`2026-06-09 04:56:33.201571+00`
- 结论详见主报告第 3 节。

## 远端部署

- 未部署远端。
- 原因：本轮为只读审查，用户未要求远端部署。

## 结论

PARTIAL_PASS。

原因：

- 代码调用链、SQL 复算、状态口径、提成口径、缓存口径和 targeted tests 已完成。
- 用户提供的页面值未能用当前 all-scope DB 快照精确复现；缺少当前登录用户 Network 请求、响应和 dataScope 证据。

## 剩余风险

- `createTime` 前端文案在订单汇总页显示为“付款时间”，但后端按 `create_time` 查询。
- 创建轨合同要求 `order_create_time`，当前后端 SQL 使用 `create_time`。
- “付费/退款卡片”的真实接口需浏览器 Network 进一步确认。
- 本轮未重启容器：仅测试与报告变更，无生产运行代码变更。

## Retro Summary

本次无需 Harness 升级。现有 Harness 已覆盖只读边界、安全检查、证据报告和 targeted tests 要求；发现的问题属于业务/前端文案与领域合同后续修复任务，不属于 Harness 执行规则缺口。
