# DDD-PRODUCT-003 Facade 路由报告

时间：2026-06-12
任务：Batch3 Replace — QuickSample 切 Facade/Port

## 变更摘要

| 原调用点 | 新调用点 | 开关 | 回退 |
|---------|---------|------|------|
| `ProductController` → `ProductQuickSampleService` | → `ProductQuickSampleApplicationService` → `ProductQuickSampleService` | `ddd.refactor.product-quick-sample.enabled` | 子开关 false 时 1:1 委派旧服务 |
| QuickSample 创建 | 开关开启时先 `LegacyProductDomainFacade` 校验 | 同上 | 关闭开关跳过 Facade |

## 新增/修改

- `ProductQuickSampleApplicationService`（新增 ApplicationService）
- `LegacyProductDomainFacade`（已存在，调整快照读 DTO）
- `ProductSnapshotReadDTO`（新增 DTO）
- `ProductController` / `ProductQuickSampleService`（调整委派）
- `DddProduct003ProductRoutingTest`（新增架构护栏测试）
- `QuickSampleApplyTest`（调整单测）

## 验证

- Backend build: PASS（`mvn -f backend/pom.xml -DskipTests package`）
- Backend scope preflight: PASS（`npm run e2e:real-pre:p0:preflight`）
- Docker: backend-real-pre healthy
- 主 evidence：`harness/reports/evidence-20260612-131449.md`
- 待跑：`mvn test` + `agent-do -Scope backend` 全量回归

## 结论

阶段性 **PASS**（待 agent-do 定稿）