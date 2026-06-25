# Retro: DDD-USER-DATASCOPE-ORDER-ATTRIBUTION

## What Changed

- Moved `OrderAttributionService` data-scope interpretation to user-domain `DataScopePolicy`.
- Kept SQL column ownership explicit at the call site: `co.user_id/co.dept_id` for page query and `user_id/dept_id` for summary query.
- Added an architecture test to prevent reintroducing local `switch(dataScope)` in this service.

## Evidence

- RED architecture test failed before implementation.
- Focused regression passed: 53 tests.
- Expanded regression passed: 142 tests.
- Backend package passed.
- local real-pre backend container was rebuilt and restarted.
- local real-pre backend health check returned UP.
- code-review-graph stats confirmed graph update at `2026-06-21T18:02:36`.

## Boundary Notes

- This is a user-domain policy consumption cleanup, not an order-domain rule change.
- No database migration, data repair, remote deployment, or permission business rule decision was made.
- The next DDD step should continue classifying remaining data-scope consumers and avoid moving UI or order attribution semantics into the user domain.

## Follow-up

- Continue `DataScopePolicy` consumption cleanup for remaining hand-written data-scope branches.
- Keep each migration narrow and covered by a boundary test plus focused service regression.
- Do not commit this slice together with unrelated historical dirty worktree changes.
