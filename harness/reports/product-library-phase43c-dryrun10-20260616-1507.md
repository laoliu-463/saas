# Product Library Phase 4-3C - RECENT_30D maxActivities=10 Dry-Run Report

- Date: 2026-06-16 15:07 CST
- Phase: 4-3C
- Scope: **RECENT_30D maxActivities=10 dry-run async** (NOT real backfill, NOT 20 activities)
- Mode: **dry-run only** (no DB writes, no confirm=true)

## 1. Pre-checks (15:07)

| Item | Result |
|---|---|
| RUNNING job | 0 |
| Redis backfill lock | 0 |
| backend health | UP |

## 2. Dry-run execution

POST /api/product-sync/admin/backfill-activity-products/async

Body:
\\\json
{
  "scope": "RECENT_30D",
  "maxActivities": 10,
  "pageSize": 20,
  "maxPagesPerActivity": 1000,
  "maxRowsPerActivity": 50000,
  "dryRun": true,
  "confirm": false,
  "displayRefreshMode": "DEFERRED"
}
\\\

Submit: HTTP 200, 0.08s
Async jobId: \product-backfill-cff8f8d6-2540-47a9-8adc-2de7339a2533\
Status on submit: RUNNING

## 3. Polling (30s interval, 191s total)

| t | status | scanned | succ | fail | apiRows | stopStats | lockWait | deadlock |
|---|---|---|---|---|---|---|---|---|
| +30s | RUNNING | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +60s | RUNNING | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +90s | RUNNING | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +120s | RUNNING | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +180s | **PARTIAL** | 10 | **7** | **3** | 4391 | {DONE_NO_MORE:7, API_ERROR:3} | 0 | 0 |

started: 2026-06-16T07:07:47.306471Z
finished: 2026-06-16T07:10:18.981975Z
duration: 191s (3 min 11 sec)

Note: scanned=0 during 0-120s reflects the Phase 4-3A limitation where job_log
flushes only on job finish (not per-activity). It is a UX issue, not a correctness issue.

## 4. Verification (post-execution)

| # | Item | Result |
|---|---|---|
| 1 | RUNNING job | 0 |
| 2 | Redis backfill lock | 0 |
| 3 | backend health | UP |
| 4 | job final status | PARTIAL (acceptable for dry-run) |
| 5 | activitiesSuccess / activitiesFailed | 7 / 3 |
| 6 | API_ERROR rate | **3/10 = 30%** |
| 7 | lockWaitCount / deadlockRetryCount | 0 / 0 |

## 5. Key comparison vs Phase 4-3 dry-run 20

| Metric | Phase 4-3 dry-run 20 | Phase 4-3C dry-run 10 | Delta |
|---|---|---|---|
| API_ERROR rate | 11/20 = 55% | 3/10 = 30% | **-25pp** (better) |
| Total activities SUCCESS | 9 | 7 | -2 (smaller batch) |
| Total activities FAILED | 11 | 3 | -8 (better absolute) |
| duration | 10 min | 3 min 11s | -7 min |
| apiRows (successful) | ~20367 / 9 = ~2264 per activity | 4391 / 7 = ~627 per activity | - |
| estimatedGapRows | 17832 (artificially high due to API_ERROR) | see below | - |

Note: estimatedGapRows is not in the polled BackfillJobStatus fields - this is a Phase 4-3A observation.

The lower API_ERROR rate (30% vs 55%) at maxActivities=10 suggests that:
- Concurrent request rate to upstream is lower
- OR the upstream rate limit window has cooled down since the previous 14:14 batch

This supports the staged approach: 5 -> 10 -> 20.

## 6. Conclusion: ACCEPTABLE FOR DRY-RUN

- API_ERROR rate 30% is below 55% baseline - improvement.
- All 7 successful activities are DONE_NO_MORE (no incomplete).
- Zero lock contention, zero deadlock retry.
- Zero side effects on real DB (dry-run).

**Allowed next phase**: Phase 4-3D (recommend)
- **Option A**: RECENT_30D maxActivities=20 dry-run (re-test the original 20-batch with 30% rate as new baseline)
- **Option B**: Phase 4-3 real backfill 5 activities (different activityIds from 4-3B's batch)
- **Option C**: RECENT_30D maxActivities=10 real backfill (5-10 ramp-up)

Recommendation: **Option A** - validate that 20-activity dry-run is now safe (low API_ERROR rate
at low concurrency suggests upstream has recovered). If Option A passes (<= 30% API_ERROR),
proceed to Option C with real backfill.

## 7. Artifacts

- \D:\Projects\SAAS\runtime\phase4-3c-submit.json\ - submit response
- \D:\Projects\SAAS\runtime\phase4-3c-jid.txt\ - jobId
- \D:\Projects\SAAS\runtime\phase4-3c-poll.log\ - polling log
