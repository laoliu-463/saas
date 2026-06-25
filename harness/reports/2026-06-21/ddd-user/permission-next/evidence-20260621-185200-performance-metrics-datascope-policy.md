# Evidence: DDD-USER-DATASCOPE-PERFORMANCE-METRICS

## Scope

- Time: 2026-06-21 18:52 Asia/Shanghai
- Environment: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- HEAD: `dd05004f`
- Worktree: not clean (`git status --short` = 50 lines)
- Remote deploy: not executed
- Commit / push: not executed to avoid mixing this slice with existing dirty worktree changes

## Change

- `PerformanceMetricsQueryService` now injects user-domain `DataScopePolicy`.
- `appendScope(...)` delegates `PERSONAL/DEPT/ALL` decision to `DataScopePolicy.decide`.
- The service keeps responsibility only for appending its SQL columns: `co.user_id` and `co.dept_id`.
- Added an architecture test that prevents local `switch(dataScope)` from returning to this query service.
- Added SQL behavior regression for `PERSONAL` data scope.

## Evidence

- RED: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyPerformanceMetricsBoundaryTest"` failed before production change because `PerformanceMetricsQueryService` did not contain `DataScopePolicy`.
- Focused tests PASS: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyPerformanceMetricsBoundaryTest,PerformanceMetricsQueryServiceTest,DataScopePolicyTest,DataScopePolicyParityTest"` -> 42 tests, 0 failures, 0 errors.
- Expanded regression PASS: `mvn -f backend/pom.xml test "-Dtest=DddUserDataScopePolicyPerformanceMetricsBoundaryTest,PerformanceMetricsQueryServiceTest,DashboardServiceTest,DashboardShadowCompareTest,DataControllerTest,DataApplicationServiceOrderSummaryCacheTest,DataScopePolicyTest,DataScopePolicyParityTest"` -> 119 tests, 0 failures, 0 errors.
- Build PASS: `mvn -f backend/pom.xml -DskipTests package` -> BUILD SUCCESS.
- Restart PASS: `harness/scripts/commands/restart-compose.ps1 -Env real-pre -Scope backend` rebuilt and recreated `backend-real-pre`.
- Health PASS: `harness/scripts/commands/verify-local.ps1 -Env real-pre -Scope backend` -> HTTP 200, body `{"status":"UP"}`.
- Code graph PASS via CLI fallback: MCP returned `Transport closed`; `code-review-graph update --repo . --skip-flows` succeeded with 75 files updated, 13038 FTS nodes. `code-review-graph status --repo .` -> last updated `2026-06-21T18:52:43`.

## Boundary

- This slice changes query-side data-scope decision consumption only.
- It does not change performance ownership, commission calculation, reversal logic, service-fee formulas, dashboard metric formulas, order facts, or historical data.
- User domain owns the data-scope decision.
- Performance metrics query service owns its SQL column mapping.

## Result

- Slice result: PASS.
- Repository DoD result: PARTIAL because commit/push and authenticated real-pre E2E were not executed.

## Risks

- JaCoCo reports stale execution data warnings for existing class mismatches; tests and package still succeeded.
- `DashboardShadowCompareTest` logs a caught Mockito strict-stubbing warning in an existing negative-path test; surefire result remains 0 failures and 0 errors.
- Authenticated real-pre UI/API E2E was not executed in this slice.
- Other hand-written data-scope branches remain in services such as `DashboardService`, `DataApplicationService`, `TalentService`, and `TalentQueryService`.
