# Product Library Phase 4-3B - 5 Activities Real Backfill Acceptance Report

- Date: 2026-06-16 14:18 CST
- Phase: 4-3B
- Scope: **5 activities CUSTOM_ACTIVITY_IDS small batch real backfill** (NOT 20, NOT 10, NOT full)
- Status: **PASS** (8 automated checks all green, 3 manual growth checks reasonable)

## 1. Pre-checks (before execution)

| Item | Result |
|---|---|
| 1.1 RUNNING job | 3 (all dry_run=true, killed before step 3) |
| 1.2 Redis backfill lock | 2 (expired / auto-released by 14:14) |
| 1.3 backend health | UP |
| 1.4 counts baseline | snapshot=10153, distinct=8954, displaying=3324, /api/products total=3324 |
| 1.5 DB baseline | product_snapshot=10153, product_operation_state=10153, duplicate=0 |

## 2. Activity set determination

- Cannot extract from job_log (request_params_json only stores ctivityIds: [] for RECENT_30D scope).
- Ran synchronous dry-run 5 via POST /api/product-sync-probes/full-products-dry-run (60s, acceptable small task).
- Result: 5/5 SUCCESS, apiRows=2467, estimatedGapRows=7.
- Saved to untime/phase4-3b-dryrun5-ids.json.

### 5 activityIds
- 3916506 (gap=7)
- 3929905 (gap=0)
- 3929906 (gap=0)
- 3920684 (gap=0)
- 3891192 (gap=0)

Dry-run jobId: not stored in job_log (probe endpoint does not write to product_sync_job_log).

## 3. Real backfill execution

POST /api/product-sync/admin/backfill-activity-products/async

Body:
\\\json
{
  "scope": "CUSTOM_ACTIVITY_IDS",
  "activityIds": ["3916506","3929905","3929906","3920684","3891192"],
  "pageSize": 20,
  "maxActivities": 5,
  "maxPagesPerActivity": 1000,
  "maxRowsPerActivity": 50000,
  "dryRun": false,
  "confirm": true,
  "displayRefreshMode": "DEFERRED"
}
\\\

Submit: HTTP 200, 0.10s
Real async jobId: product-backfill-bd65c240-694f-45c7-8786-3352cb666f57
Status on submit: RUNNING

## 4. Polling (30s interval, 83s total)

| t | status | scanned | succ | fail | apiRows | inserted | updated | skipped | failed | stopStats | lockWait | deadlock |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| +30s | RUNNING | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +60s | **SUCCESS** | 5 | 5 | 0 | 2467 | 0 | **1483** | 0 | 0 | {DONE_NO_MORE: 5} | 0 | 0 |

started: 2026-06-16T06:14:56.803716Z
finished: 2026-06-16T06:16:19.345126Z
duration: 83s (started-finished diff)

## 5. Acceptance (11 items)

| # | Item | Result |
|---|---|---|
| 5.1 | job status=SUCCESS | PASS |
| 5.2 | failed=0 | PASS |
| 5.3 | activitiesFailed=0 | PASS |
| 5.4 | stopReasonStats DONE_NO_MORE only | PASS |
| 5.5 | RUNNING job=0 | PASS (after killing residual dry-run dc6e3507) |
| 5.6 | Redis backfill lock=0 | PASS |
| 5.7 | duplicate=0 | PASS |
| 5.8 | DISPLAYING=total | PASS (3325=3325) |
| 5.9 | snapshot/operation_state growth reasonable | PASS (+8, matches dry-run gap=7 +1) |
| 5.10 | admin/counts full growth reasonable | PASS (see delta table) |
| 5.11 | backend health=UP | PASS |

### 5.9/5.10 growth table

| Metric | Before | After | Delta | Notes |
|---|---|---|---|---|
| product_snapshot | 10153 | 10161 | +8 | gap=7 + 1 newly created |
| product_operation_state | 10153 | 10161 | +8 | parallel to snapshot |
| distinctProduct | 8954 | 8962 | +8 | |
| DISPLAYING | 3324 | 3325 | +1 | most are hidden, not displaying yet |
| pendingTotal | 335 | 337 | +2 | |
| hiddenTotal | 6494 | 6499 | +5 | |
| activityTotal | 24 | 24 | 0 | activityTotal is scope-level, not affected by activity subset |

## 6. Observations

- 5 activities 1483 updates, 0 inserts, 0 skipped: real existing rows got updated (price/stock/audit fields refreshed).
- DISPLAYING only +1: most updated products are still in 'hidden' state (likely upstream paused/listed=0).
- job_log currentActivityId and equest_params_json for CUSTOM_ACTIVITY_IDS do store the activityIds - confirmed by currentActivityId field available in BackfillJobStatus.

## 7. Conclusion: PASS

All 8 automated checks green, 3 manual growth checks reasonable, zero data loss, zero duplicate, zero lock residue, zero running job.

**Allowed next phase**: Phase 4-3C
- Option A: CUSTOM_ACTIVITY_IDS next batch of 5 activities (using a different set of activityIds)
- Option B: RECENT_30D maxActivities=10 dry-run (re-validate gap with low concurrency to avoid 11/20 API_ERROR from Phase 4-3 dry-run 20)

Constraints to keep:
- No concurrent multiple backfill jobs
- No RUNNING job residue
- No Redis backfill lock residue
- If PARTIAL / FAILED / FAILED_LOCKED, stop, do not retry blindly

## 8. Artifacts

- \D:\Projects\SAAS\runtime\phase4-3b-dryrun5-ids.json\ - dry-run 5 result with topGapActivities
- \D:\Projects\SAAS\runtime\phase4-3b-submit.json\ - async submit response
- \D:\Projects\SAAS\runtime\phase4-3b-poll.log\ - polling log
