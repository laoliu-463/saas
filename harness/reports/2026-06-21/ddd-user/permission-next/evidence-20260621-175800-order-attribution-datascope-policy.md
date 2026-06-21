# Evidence: DDD-USER-DATASCOPE-ORDER-ATTRIBUTION

## Scope

- Time: 2026-06-21 17:58 Asia/Shanghai
- Environment: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- HEAD: `f927250c`
- Worktree: not clean (`git status --short` = 54 lines)
- Remote deploy: not executed
- Commit / push: not executed to avoid mixing this slice with existing dirty worktree changes

## Change

- `OrderAttributionService` now injects user-domain `DataScopePolicy`.
- Unattributed order page filtering delegates `co.user_id/co.dept_id` to `DataScopePolicy.applyTo`.
- Attribution summary query filtering delegates `user_id/dept_id` to `DataScopePolicy.applyTo`.
- Removed local `switch(dataScope)` responsibility from order attribution query service.
- Updated direct service/controller tests to construct `OrderAttributionService` with `DataScopePolicy`.
- Added architecture test guarding this boundary.

## Evidence

- RED: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyOrderAttributionBoundaryTest"` failed before production change because `OrderAttributionService` did not contain `DataScopePolicy`.
- Focused tests PASS: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyOrderAttributionBoundaryTest,OrderAttributionServiceTest,OrderAttributionControllerTest,DataScopePolicyTest,DataScopePolicyParityTest"` -> 53 tests, 0 failures, 0 errors.
- Expanded regression PASS: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyOrderAttributionBoundaryTest,OrderAttributionServiceTest,OrderAttributionControllerTest,DataScopePolicyTest,DataScopePolicyParityTest,OrderServiceTest,LegacyOrderDomainFacadeTest,DddOrder003RoutingTest,OrderControllerTest,OrderSyncControllerTest,CurrentUserControllerTest,UserDomainServiceTest,DataScopeAspectTest"` -> 142 tests, 0 failures, 0 errors.
- Build PASS: `mvn -f backend/pom.xml -DskipTests package` -> BUILD SUCCESS.
- Restart PASS: `harness/scripts/commands/restart-compose.ps1 -Env real-pre -Scope backend` rebuilt and recreated `backend-real-pre`.
- Health PASS: `harness/scripts/commands/verify-local.ps1 -Env real-pre -Scope backend` -> HTTP 200, body `{"status":"UP"}`.
- Code graph PASS: `list_graph_stats_tool` -> status `ok`, files 1632, nodes 13034, edges 148076, last updated `2026-06-21T18:02:36`.

## Boundary

- This slice changes query-side data-scope filtering only.
- It does not change order facts, attribution rules, order sync, performance event publication, commission calculation, or historical data.
- User domain owns `PERSONAL/DEPT/ALL` data-scope interpretation through `DataScopePolicy`.
- Order attribution service remains responsible for composing order-attribution queries.

## Result

- Slice result: PASS.
- Repository DoD result: PARTIAL because commit/push and authenticated real-pre E2E were not executed.

## Risks

- JaCoCo reports stale execution data warnings for existing class mismatches; tests and package still succeeded.
- Authenticated real-pre UI/API E2E was not executed in this slice.
- Other hand-written data-scope branches remain in services such as `DashboardService`, `DataApplicationService`, `TalentService`, `TalentQueryService`, and `PerformanceMetricsQueryService`.
