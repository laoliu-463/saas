# Evidence: DDD-USER-DATASCOPE-DASHBOARD

## 基本信息

- 时间：2026-06-21 19:08 Asia/Shanghai
- 环境：本地 `real-pre`
- 分支：`feature/ddd/DDD-VERIFY-001`
- HEAD：`dd05004f264a`
- 远端部署：未执行，用户未要求
- Git 状态：工作区非干净，包含本轮文件和既有历史脏文件；本轮未提交、未推送
- 结论：小切片 `PASS`；仓库 Definition of Done 因未提交 / 未推送 / 未执行授权 E2E，整体记为 `PARTIAL`

## 修改范围

- `DataScopePolicy` 新增 `requiresFilter(DataScope)`，表达 PERSONAL / DEPT 缺少上下文时仍需要 fail-closed。
- `DashboardService` 注入用户域 `DataScopePolicy`，移除本地 `switch(dataScope)`。
- `DashboardService` 的 SQL clause 构造与 `QueryWrapper` 过滤改为消费 `DataScopePolicy.decide`。
- `DashboardServiceTest` 补充 PERSONAL 缺少 userId 时 `1 = 0` 的兼容行为。
- 新增 `DddUserDataScopePolicyDashboardBoundaryTest` 防止 Dashboard 重新维护本地 `switch(dataScope)`。
- `DashboardShadowCompareTest` 更新构造参数。
- `UBIQUITOUS_LANGUAGE.md` 补充“分析看板”术语，明确其只消费数据范围，不拥有归因或指标公式。

## 验证证据

- RED：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDashboardBoundaryTest"` 先失败，原因是 `DashboardService` 尚未依赖 `DataScopePolicy`。
- Focused PASS：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDashboardBoundaryTest,DashboardServiceTest,DashboardShadowCompareTest,DataScopePolicyTest,DataScopePolicyParityTest"`，67 tests，0 failures，0 errors。
- Expanded PASS：`mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyDashboardBoundaryTest,DashboardServiceTest,DashboardControllerTest,DashboardShadowCompareTest,DataControllerTest,PerformanceMetricsQueryServiceTest,DataScopePolicyTest,DataScopePolicyParityTest"`，117 tests，0 failures，0 errors。
- Package PASS：`mvn -f backend/pom.xml -DskipTests package`，BUILD SUCCESS，生成 `backend/target/colonel-saas.jar`。
- Restart PASS：`restart-compose.ps1 -Env real-pre -Scope backend` 已 rebuild / recreate `backend-real-pre`。
- Health PASS：`verify-local.ps1 -Env real-pre -Scope backend`，`/api/system/health` 返回 `{"status":"UP"}`。
- Graph PASS：`code-review-graph update --repo . --skip-flows` 后 `status` 为 13043 nodes / 148164 edges / 1633 files，last updated `2026-06-21T19:07:54`。
- Switch scan PASS：`DashboardService` 已无 `switch(dataScope)`；剩余 5 处集中在 `DataApplicationService`。
- Harness limits PASS：`harness/scripts/check-harness-limits.ps1` 返回 `PASS`。
- Whitespace PASS：目标文件 trailing whitespace 扫描返回 `NO_TRAILING_WHITESPACE`。
- Line count PASS：`DOMAIN_STATUS.md` 154 行，evidence 34 行，retro 22 行，均未超过 200 行。
- Report dir PASS：`datascope-next` 2 个文件，`permission-next` 保持 10 个文件。

## 未执行项

- 未执行真实登录态页面 E2E，缺少授权账号与本轮必要性。
- 未执行远端 `real-pre` 部署。
- 未提交、未推送，工作区存在大量历史脏文件，避免混入无关变更。

## 风险

- `DashboardShadowCompareTest` 中存在既有 Mockito strict-stubbing 日志，但 surefire 结果为 PASS。
- `mvn package` 仍有既有 Jacoco execution data 与 class 不匹配警告。
- `DataApplicationService` 仍有 5 处本地 `switch(dataScope)`，应作为下一小切片继续收口。
