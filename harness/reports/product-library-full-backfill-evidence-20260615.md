# Product Library Full Backfill Evidence

## Metadata

- Time: 2026-06-15 11:58 +08:00
- Environment: real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Commits: 4f9977c9, 4711014b
- Remote deploy: false

## Code Verification

- Target unit tests: PASS, 34 tests.
- Related product-sync tests: PASS, 60 tests.
- Backend build: PASS via `agent-do.ps1`.
- Frontend build: PASS via `agent-do.ps1`.
- Docker restart: PASS, backend/frontend recreated.
- Health check: PASS, backend `/api/system/health`, frontend `/healthz`.
- P0 business preflight: PASS, `npm run e2e:real-pre:p0:preflight`.

## Schema Verification

- Applied only `alter-product-activity-backfill-state-20260615.sql`.
- Did not rerun full `migrate-all.sql`, because runbook documents historical non-idempotent DML risk.
- Tables present: `product_sync_job_log`, `product_activity_sync_state`.
- Indexes present:
  - `uk_product_sync_job_log_job_id`
  - `idx_product_sync_job_log_type_started`
  - `uk_product_activity_sync_state_activity_scope`
  - `idx_product_activity_sync_state_status`

## API Verification

- Dry-run endpoint:
  - Request: `POST /api/product-sync/admin/backfill-activity-products`
  - Scope: `CUSTOM_ACTIVITY_IDS`
  - Activity: `3859423`
  - Limits: `maxPagesPerActivity=1`, `maxRowsPerActivity=20`
  - Result: `code=200`, `dryRun=true`, `activitiesScanned=1`, `apiFetchedRows=20`
  - Stop reason: `MAX_ROWS_REACHED=1`, `activitiesIncomplete=1`
  - Business rows unchanged: `product_snapshot 2000 -> 2000`, `product_operation_state 2000 -> 2000`
  - Job log changed: `0 -> 1`

- Real backfill without confirm:
  - Request: same activity, `dryRun=false`, `confirm=false`
  - Result: business `code=400`, `data=null`
  - Business rows unchanged: `2000 -> 2000`
  - Job log unchanged: `1 -> 1`

- Admin counts:
  - `GET /api/products/admin/counts`: `code=200`
  - `snapshotTotal=7983`, `relationTotal=7983`, `distinctProductTotal=7024`
  - `displayingTotal=2978`, `pendingTotal=252`, `hiddenTotal=4753`, `activityTotal=24`

- Existing product list:
  - `GET /api/products?page=1&size=100`: `code=200`
  - `total=2978`, `records=100`
  - Evidence supports `/api/products` still uses DISPLAYING list semantics.

## Conclusion

PARTIAL for real data backfill execution; PASS for code fix, schema readiness, dry-run behavior, confirm guard, build, restart, health, and P0 validation.

True write backfill was not executed because the task requires explicit confirm after dry-run.

## Residual Risk

- real-pre total product rows are not fully backfilled until a confirmed real backfill is run.
- The HTTP wrapper returns `code=400` for missing confirm but the response message is null.
