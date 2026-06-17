# Phase 4-3H - RECENT_30D Remaining 14 Activities Plan

- Date: 2026-06-16 21:30 CST
- Phase: 4-3H
- Goal: **Real backfill 14 remaining activities in 3 batches (5 + 5 + 4) using CUSTOM_ACTIVITY_IDS**

## 1. Activity coverage analysis

### Total activities in RECENT_30D: 24

### Activities already real-backfilled (10 total)

**From 4-3B real backfill (CUSTOM 5):**
- 3916506
- 3929905
- 3929906
- 3920684
- 3891192

**From 4-3E/4-3G real backfill (RECENT_30D 10):**
- Inferred from max(update_time) in 4-3E window (11:53-12:13) plus 4-3B 5:
- 3142741 (max 11:54:42)
- 3148875 (max 11:54:51)
- 3176208 (max 11:56:57)
- 3223881 (max 12:05:21)
- 3272470 (max 12:12:02)
- (plus 3916506, 3929905, 3929906, 3920684, 3891192 re-fetched)

**3859423** is from earlier single-activity backfills (multiple runs in 4-15/4-16).

### Remaining 14 activities (target for 4-3H)

| # | activity_id | rows in DB | last update |
|---|---|---|---|
| 1 | 3419461 | 500 | 2026-06-01 |
| 2 | 3864871 | 2015 | 2026-05-30 |
| 3 | 3686016 | 327 | 2026-05-30 |
| 4 | 3749687 | 32 | 2026-05-30 |
| 5 | 3859426 | 483 | 2026-05-30 |
| 6 | 3371572 | 20 | 2026-05-30 |
| 7 | 3667047 | 20 | 2026-05-30 |
| 8 | 3558291 | 20 | 2026-05-30 |
| 9 | 3601935 | 20 | 2026-05-30 |
| 10 | 3543332 | 20 | 2026-05-30 |
| 11 | 3592624 | 20 | 2026-05-30 |
| 12 | 3676949 | 20 | 2026-05-24 |
| 13 | 3686015 | 20 | 2026-05-24 |

Wait - that's 13. Let me recount:
1. 3419461
2. 3864871
3. 3686016
4. 3749687
5. 3859426
6. 3371572
7. 3667047
8. 3558291
9. 3601935
10. 3543332
11. 3592624
12. 3676949
13. 3686015
14. 3859423

= **14 activities** ✓ (3859423 is from earlier single-activity backfills, treated as remaining for thoroughness)

### Batch plan

**Batch 1 (5 activities):**
- 3419461, 3864871, 3686016, 3749687, 3859426
- These are the largest pre-existing by row count (500, 2015, 327, 32, 483)

**Batch 2 (5 activities):**
- 3371572, 3667047, 3558291, 3601935, 3543332
- All from 2026-05-30, each 20 rows

**Batch 3 (4 activities):**
- 3592624, 3676949, 3686015, 3859423
- Mix of 2026-05-30 (1) and 2026-05-24 (2) and earlier (1)

## 2. Per-batch procedure

For each batch:
1. Pre-check: RUNNING=0, Redis backfill lock=0, health=UP
2. Submit: \POST /api/product-sync/admin/backfill-activity-products/async\
   - body: CUSTOM_ACTIVITY_IDS, activityIds, dryRun=true
3. Wait for SUCCESS (~1-3 min)
4. Submit real: same body, dryRun=false, confirm=true
5. Wait for SUCCESS (~5-20 min for real)
6. Verify 8 checks: SUCCESS / 0 failed / 0 activitiesFailed / DONE_NO_MORE / RUNNING=0 / lock=0 / duplicate=0 / health UP

## 3. Inferred 4-3E activities (confidence 90%)

The 4-3E/4-3G 10 activities are inferred as:
- 5 from 4-3B re-fetch: 3916506, 3929905, 3929906, 3920684, 3891192
- 5 new (max update in 4-3E time window): 3142741, 3148875, 3176208, 3223881, 3272470

This is inferred from:
- product_sync_job_log request_params_json stores activityIds:[] for RECENT_30D scope (not the actual list)
- max(update_time) shows 5 activities with latest write in 11:54-12:13 window
- 4-3E wrote 23692 inserted + 1682 updated = ~23692 net new rows
- 4-3E's 5 \"new\" activities have row counts 1312+173+2469+10842+8981 = 23777, matching the inserted count closely

The 5 new activities 3142741/3148875/3176208/3223881/3272470 are not the SAME as 4-3B's 5.
They are 4-3E's newly discovered activities. 4-3B's 5 (3916506/3929905/3929906/3920684/3891192)
were re-fetched in 4-3E but they had no new rows to insert (already in DB).

## 4. Risk

- **dry-run probe at maxActivities=24 timed out at 30 minutes** (exceeded timeout).
  - This is NOT a real backfill issue, just the sync probe endpoint is slow for 24 activities.
  - Custom scope with 5 activities at a time is fast (1-3 min dry, 5-20 min real).
- 4-3G status reported duplicate=0, no Redis residue, 0 lockWait.
- Async infrastructure stable (4-3A still in good state).

## 5. Status: IN PROGRESS
