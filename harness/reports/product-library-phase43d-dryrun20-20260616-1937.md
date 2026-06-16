# Product Library Phase 4-3D - RECENT_30D maxActivities=20 Dry-Run Re-test Report

- Date: 2026-06-16 19:37 CST
- Phase: 4-3D
- Scope: **RECENT_30D maxActivities=20 dry-run async re-test** (re-validate API_ERROR rate)
- Mode: **dry-run only** (no DB writes)

## 1. Pre-checks (15:28)

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
  "maxActivities": 20,
  "pageSize": 20,
  "maxPagesPerActivity": 1000,
  "maxRowsPerActivity": 50000,
  "dryRun": true,
  "confirm": false,
  "displayRefreshMode": "DEFERRED"
}
\\\

Submit: HTTP 200, 0.05s
Async jobId: \product-backfill-676943b3-317f-4bdb-855e-141d6af9052b\
Status on submit: RUNNING

## 3. Polling (30s interval, 4h 6min total)

| t | status | scanned | succ | fail | apiRows | stopStats | lockWait | deadlock |
|---|---|---|---|---|---|---|---|---|
| +30s ~ +240s | RUNNING | 0 | 0 | 0 | 0 | {} | 0 | 0 |
| +15000s (4h 6min) | **PARTIAL** | 20 | **8** | **12** | 11568 | {DONE_NO_MORE:8, API_ERROR:12} | 0 | 0 |

started: 2026-06-16T07:29:02.506990Z
finished: 2026-06-16T11:34:45.125850Z
duration: 14743s (4h 6min)

Note: scanned=0 during 0-240s polling reflects the Phase 4-3A limitation
(job_log flushes only on job finish). The 4h 6min duration is much longer than
Phase 4-3 dry-run 20 (10 min) and Phase 4-3C dry-run 10 (3 min 11s) - the
likely cause is applicationTaskExecutor pool (core=4 max=8) being saturated
by other concurrent backfill dry-run jobs in the window.

## 4. Verification (post-execution 19:37)

| # | Item | Result |
|---|---|---|
| 1 | RUNNING job | 0 |
| 2 | Redis backfill lock | **2 (RESIDUE: global + activity 3916506)** |
| 3 | backend health | UP |
| 4 | job final status | PARTIAL |
| 5 | activitiesSuccess / activitiesFailed | 8 / 12 |
| 6 | API_ERROR rate | **12/20 = 60%** |
| 7 | lockWaitCount / deadlockRetryCount | 0 / 0 |

## 5. Key comparison vs prior phases

| Phase | scope | maxActivities | dryRun | API_ERROR rate | duration | date |
|---|---|---|---|---|---|---|
| 4-3 | RECENT_30D | 20 | t | 11/20 = **55%** | 10 min | 12:35 |
| 4-3C | RECENT_30D | 10 | t | 3/10 = **30%** | 3 min 11s | 15:10 |
| 4-3D | RECENT_30D | 20 | t | 12/20 = **60%** | 4h 6min | 19:34 |

**API_ERROR rate trend: 55% -> 30% -> 60%** (regression at 20 activities)

The 30% rate at 10 activities is the best so far. Returning to 20 activities
regresses to 60% (worse than baseline). This suggests:
- Upstream rate limit windows are tight at 20 concurrent
- 10 activities is a safer batch size
- The 4h 6min duration further suggests applicationTaskExecutor was saturated

## 6. Conclusion: REGRESSION

- **API_ERROR rate 60% is WORSE than baseline 55%** - 20-activity batches
  are not safe at this upstream rate limit window.
- **Redis lock residue 2 keys** - finishJob path did not release locks.
  This is a Phase 4-3A bug (releaseWithOwner may have failed or not run).
- **4h 6min duration** - applicationTaskExecutor pool saturation
  (likely caused by other concurrent dry-run jobs in the window).

**Per user constraint** "if real backfill FAILED/PARTIAL/API_ERROR, stop, do not retry blindly":
- 4-3D is a dry-run, not real backfill, so the strict stop rule does not trigger.
- However, the **trend** is clear: 20-activity batches are not safe.

## 7. Recommended next steps (NOT executed)

| Option | Description | Risk | Expected API_ERROR |
|---|---|---|---|
| **B1** | RECENT_30D maxActivities=10 **real backfill** (gradual ramp from dry-run 10 at 30% rate) | Medium | 30% (same as dry-run) |
| **B2** | CUSTOM_ACTIVITY_IDS 5 activities real backfill (use different set from 4-3B) | Low | ~0% (specific known activityIds) |
| **B3** | RECENT_30D maxActivities=10 dry-run again (confirm 30% is stable, not lucky) | None (dry-run) | 30% +/- noise |
| C | RECENT_30D maxActivities=20 real backfill | High | 60% (regression expected) |

**Recommendation**: **B2** (CUSTOM_ACTIVITY_IDS 5 activities real backfill, different set
from 4-3B) - lowest risk, validates the real-write path on a fresh set of activities
without exposing the 20-activity upstream rate limit issue.

## 8. Artifacts

- \D:\Projects\SAAS\runtime\phase4-3d-submit.json\ - submit response
- \D:\Projects\SAAS\runtime\phase4-3d-poll.log\ - polling log (partial, session interrupted)
- \D:\Projects\SAAS\runtime\phase4-3d-poll2.log\ - second polling log (empty, command failed)
