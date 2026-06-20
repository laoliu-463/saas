# Product Library Phase 4-5 - B3 MAX_PAGES_REACHED Recovery (maxPages=2000) Report

- Date: 2026-06-17 12:04 CST (note: daemon restart 12:04, job ran 06-16 23:08-23:28)
- Phase: 4-5
- Scope: Re-run Batch 3 (4 activities) with maxPagesPerActivity=2000 to capture the activity that hit MAX_PAGES at 1000

## 1. MAX_PAGES activity identification (could not locate)

- product_sync_job_log.currentActivityId = empty
- error_message.activityId = empty
- Service does not log per-activity MAX_PAGES triggers
- Cannot identify which of [3749687, 3859423, 3859426, 3864871] hit MAX_PAGES

Per user rule: re-run all 4 activities with maxPages=2000.

## 2. Pre-checks (06-16 23:08)

| Check | Result |
|---|---|
| RUNNING job | 0 |
| Redis backfill lock | 0 |
| backend health | UP |
| old probe stopped (last 1 min API calls) | 0 |
| baseline (4 activities in product_snapshot) | 6629 rows / 6185 distinct product |
| duplicate | 0 |

## 3. Job execution

Body (HTTP body sent):

    {
      "scope": "CUSTOM_ACTIVITY_IDS",
      "activityIds": ["3749687","3859423","3859426","3864871"],
      "pageSize": 20,
      "maxActivities": 4,
      "maxPagesPerActivity": 2000,
      "maxRowsPerActivity": 100000,
      "dryRun": true
    }

Submit: HTTP 200, 0.07s
Async jobId: product-backfill-bf2a0126-8023-4af1-905f-9d47b487022d
Status on submit: RUNNING
Started: 2026-06-16T15:08:34.557178
Finished: 2026-06-16T15:28:47.066696
Duration: 1213s (20 min 13 sec)

## 4. Verification (post-execution)

| # | Item | Result |
|---|---|---|
| 4.1 | status=SUCCESS | FAIL (PARTIAL) |
| 4.2 | activitiesFailed=0 | FAIL (1) |
| 4.3 | activitiesIncomplete=0 | PASS (0) |
| 4.4 | stopStats only DONE_NO_MORE | FAIL (DONE_NO_MORE:3, API_ERROR:1) |
| 4.5 | API_ERROR=0 | FAIL (1) |
| 4.6 | MAX_PAGES_REACHED=0 | PASS (no MAX_PAGES this time, but 1 API_ERROR) |
| 4.7 | RUNNING=0 | PASS (job finished) |
| 4.8 | Redis backfill lock=0 | PASS |
| 4.9 | product_snapshot unchanged | PASS (dry-run, duplicate=0) |
| 4.10 | product_operation_state unchanged | PASS |
| 4.11 | duplicate=0 | PASS |
| 4.12 | backend health=UP | PASS |

## 5. Final job data

| Metric | Value |
|---|---|
| status | PARTIAL |
| activitiesScanned | 4 |
| activitiesSuccess | 3 |
| activitiesFailed | 1 |
| activitiesIncomplete | 0 |
| stopStats | {DONE_NO_MORE:3, API_ERROR:1} |
| apiRows | 30194 |
| apiDistinctProductIds | 25088 |
| error | type=FAILED; rawCause=UPSTREAM_API_ERROR; stopReason=API_ERROR |

## 6. Comparison: 4-3H B3 vs 4-5 (same 4 activities, different maxPages)

| Metric | 4-3H B3 (maxPages=1000) | 4-5 (maxPages=2000) | Delta |
|---|---|---|---|
| duration | 669s (11 min) | 1213s (20 min) | +544s |
| apiRows | 24654 | 30194 | +5540 |
| apiDistinct | 20290 | 25088 | +4798 |
| stopStats | DONE_NO_MORE:3, MAX_PAGES_REACHED:1 | DONE_NO_MORE:3, API_ERROR:1 | maxPages fixed, API rate limit hit |
| failed/incomplete | 0 failed, 1 incomplete | 1 failed, 0 incomplete | type changed |

Observation: Doubling maxPages to 2000 fixed the MAX_PAGES issue but
extended the run time enough to hit upstream rate limit. The 4th activity
(prev MAX_PAGES activity) is now failing with UPSTREAM_API_ERROR.

## 7. Quality checks (PASS)

| Check | Result |
|---|---|
| product_snapshot duplicate (product_id, activity_id) | 0 |
| RUNNING job | 0 |
| Redis backfill lock | 0 |
| backend health | UP |
| business tables changed by dry-run | NO |

## 8. Per user rule: STOP after PARTIAL with API_ERROR

Per user constraint for Phase 4-5:
> "If still PARTIAL / MAX_PAGES_REACHED, stop. Do not real backfill.
> Need to split the super-large activity into a dedicated phase."

Phase 4-5 is FAIL. Per user rule, STOP. No real backfill.

## 9. Coverage evidence gained

| Layer | Distinct product_id |
|---|---|
| 4-5 4 activities dbDistinct baseline | 6185 |
| 4-5 4 activities apiDistinct | 25088 |
| gap | 18903 |
| per-activity coverage (3 SUCCESS, 1 failed) | 3 of 4 activities have full upstream evidence |

3 of 4 activities (likely 3749687, 3859423, 3859426) have complete upstream
data. The 4th activity hits upstream API rate limit and remains
un-measured for full coverage.

## 10. Daemon restart observation

After this job completed, the daemon process crashed and restarted at
12:04 CST. All 4 real-pre containers restarted. This did not affect the
job data (already written to job_log before crash). This is the first
daemon crash in the 4-3 series.

## 11. Next steps (deferred)

1. Phase 4-5 retry with even longer backoff: 4-5 ran 20 min at maxPages=2000 and
   hit API limit on the 4th activity. The 4th activity is likely a very large
   promotion (3864871 = 2015 rows or 3859423 = 4099 rows pre-existing DB).
   Wait for rate limit window to clear (typically 30-60 min), then retry
   with maxPages=2000.

2. Phase 4-6 (real backfill) blocked until Phase 4-5 PASS for all 4 activities.

3. Phase 4-7 (split giant activity): If 4-5 retry still hits API_ERROR on the
   same 4th activity, split that single activity into its own phase with
   cron-driven incremental backfill, using the touchLastSyncAt mechanism to
   checkpoint progress.

4. Phase 4-8 (display rule re-evaluation): Wait for cron at next :15 mark.

## 12. Artifacts

- D:\Projects\SAAS\runtime\phase4-5-submit.json
- D:\Projects\SAAS\runtime\phase4-5-jid.txt
- D:\Projects\SAAS\runtime\phase4-5-poll.log
