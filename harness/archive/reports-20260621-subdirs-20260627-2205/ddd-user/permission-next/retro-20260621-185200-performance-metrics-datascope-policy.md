# Retro: DDD-USER-DATASCOPE-PERFORMANCE-METRICS

## What Changed

- Moved `PerformanceMetricsQueryService` data-scope decision handling to user-domain `DataScopePolicy`.
- Kept SQL column names in the performance query service because those are local query details, not user-domain rules.
- Added an architecture test to prevent reintroducing `switch(dataScope)` in this service.

## Evidence

- RED architecture test failed before implementation.
- Focused regression passed: 42 tests.
- Expanded regression passed: 119 tests.
- Backend package passed.
- local real-pre backend container was rebuilt and restarted.
- local real-pre backend health check returned UP.
- code-review-graph MCP failed with `Transport closed`; CLI fallback updated graph successfully at `2026-06-21T18:52:43`.

## Boundary Notes

- This is a user-domain policy consumption cleanup, not a performance-domain rule change.
- No database migration, data repair, remote deployment, or formula decision was made.
- The next DDD step should continue classifying remaining data-scope consumers.

## Follow-up

- Continue with `DashboardService`, `DataApplicationService`, `TalentService`, or `TalentQueryService` hand-written data-scope branches.
- Keep each migration narrow and covered by one boundary test plus focused service regression.
- Do not commit this slice together with unrelated historical dirty worktree changes.
