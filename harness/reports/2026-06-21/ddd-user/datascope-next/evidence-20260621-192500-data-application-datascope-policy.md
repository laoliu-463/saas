# Evidence: DDD-USER-DATASCOPE-DATA-APPLICATION

## 基本信息

- 时间：2026-06-21 19:25 Asia/Shanghai
- 环境：本地 `real-pre`
- 分支：`feature/ddd/DDD-VERIFY-001`
- HEAD：`8426ea0bfc88`
- 远端部署：未执行，用户未要求
- Git 状态：工作区非干净，包含本轮文件和既有历史脏文件；本轮未提交、未推送
- 结论：小切片 `PASS`；仓库 Definition of Done 因未提交 / 未推送 / 未执行授权 E2E，整体记为 `PARTIAL`

## 修改范围

- `DataScopePolicy` 新增 `ContextRequirement` / `contextRequirement`，由用户域判断 PERSONAL / DEPT 所需上下文是否缺失。
- `DataApplicationService` 注入用户域 `DataScopePolicy`，移除 5 处本地 `switch(dataScope)`。
- `DataApplicationService` 继续保留缺少 user/dept 上下文时抛 `BusinessException` 的既有行为。
- `DataController`、`DataControllerTest` 和 `DataApplicationServiceOrderSummaryCacheTest` 更新构造依赖。
- 新增 `DddUserDataScopePolicyDataApplicationBoundaryTest` 防止数据页服务重新维护本地 `switch(dataScope)`。
- `UBIQUITOUS_LANGUAGE.md` 补充“数据页订单明细”术语，明确其是只读视图，不是归因或重算入口。

## 验证证据

- RED：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDataApplicationBoundaryTest"` 先失败，原因是 `DataApplicationService` 尚未依赖 `DataScopePolicy` 且仍存在 `switch(dataScope)`。
- Focused PASS：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDataApplicationBoundaryTest,DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,DataApplicationServiceOrderSummaryCacheTest,DataScopePolicyTest,DataScopePolicyParityTest"`，89 tests，0 failures，0 errors。
- Expanded PASS：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDataApplicationBoundaryTest,DddUserDataScopePolicyDashboardBoundaryTest,DddUserDataScopePolicyPerformanceMetricsBoundaryTest,DddUserDataScopePolicyOrderAttributionBoundaryTest,DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,DataApplicationServiceOrderSummaryCacheTest,DashboardServiceTest,DashboardControllerTest,DashboardShadowCompareTest,PerformanceMetricsQueryServiceTest,DataScopePolicyTest,DataScopePolicyParityTest"`，130 tests，0 failures，0 errors。
- Package PASS：`mvn -f backend/pom.xml -DskipTests package`，BUILD SUCCESS。
- Restart PASS：`restart-compose.ps1 -Env real-pre -Scope backend` 已 rebuild / recreate `backend-real-pre`。
- Health PASS：`verify-local.ps1 -Env real-pre -Scope backend`，`/api/system/health` 返回 `{"status":"UP"}`。
- Graph PASS：`code-review-graph update --repo . --skip-flows` 后 `status` 为 13052 nodes / 148197 edges / 1634 files，last updated `2026-06-21T19:24:06`。
- Switch scan PASS：`OrderAttributionService`、`PerformanceMetricsQueryService`、`DashboardService`、`DataApplicationService` 均无 `switch(dataScope)`。

## 未执行项

- 未执行真实登录态页面 E2E，缺少授权账号与本轮必要性。
- 未执行远端 `real-pre` 部署。
- 未提交、未推送，工作区存在大量历史脏文件，避免混入无关变更。

## 风险

- `DashboardShadowCompareTest` 仍输出既有 Mockito strict-stubbing warning，但 surefire 结果为 PASS。
- `mvn package` 仍有既有 Jacoco execution data 与 class 不匹配警告。
- 真实 admin/group/self 多账号 API 差异未在本轮执行，需后续授权态 E2E 或专项 API 对比补证据。
