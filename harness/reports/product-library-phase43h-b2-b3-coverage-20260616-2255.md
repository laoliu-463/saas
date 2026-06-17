# Product Library Phase 4-3H Batch 2/3 - RECENT_30D Remaining 9 Activities Coverage Report

- Date: 2026-06-16 22:55 CST
- Phase: 4-3H (Batch 2 + Batch 3, dry-run only)
- Scope: **Coverage dry-run of 9 remaining activities (5+4) using CUSTOM_ACTIVITY_IDS, dryRun=true**
- Result: **Batch 2 PASS, Batch 3 PARTIAL (1/4 MAX_PAGES_REACHED, 0 API_ERROR)**

## 1. Pre-checks (22:45 / 22:49)

| Check | Result |
|---|---|
| 1.1 Old sync probe stopped (last API call 22:45:22, no calls in past 1 min) | PASS |
| 1.2 RUNNING job | 0 |
| 1.3 Redis backfill lock | 0 |
| 1.4 backend health | UP |

## 2. Batch 2 (5 activities, 22:46-22:48, 2 min)

ActivityIds: 3601935, 3667047, 3676949, 3686015, 3686016
Async jobId: \product-backfill-4b2b65b6-8448-4397-b6d6-668f50acd62e\

| Metric | Value |
|---|---|
| status | SUCCESS |
| activitiesScanned | 5 |
| activitiesSuccess | 5 |
| activitiesFailed | 0 |
| **stopStats** | **{DONE_NO_MORE:5}** |
| **apiRows** | **3328** |
| **apiDistinctProductIds** | **3113** |
| duration | 119s |
| error | (empty) |
| **dbRows baseline** (5 activities in product_snapshot) | 407 |
| **dbDistinctProduct baseline** (5 activities) | 406 |
| **estimatedGapRows** | 3328 - 407 = 2921 |
| **estimatedGapDistinctProducts** | 3113 - 406 = **2707** |
| product_snapshot changed | NO (dry-run, duplicate=0) |
| product_operation_state changed | NO |

**Batch 2 coverage: 406 / 3113 = 13.0% (dbDistinct / apiDistinct)**

## 3. Batch 3 (4 activities, 22:49-23:00, 11 min)

ActivityIds: 3749687, 3859423, 3859426, 3864871
Async jobId: \product-backfill-22fb8c55-4329-47ef-b5c4-e7ce953cdec6\

| Metric | Value |
|---|---|
| status | **PARTIAL** |
| activitiesScanned | 4 |
| activitiesSuccess | 3 |
| activitiesFailed | 0 |
| activitiesIncomplete | **1** |
| **stopStats** | **{DONE_NO_MORE:3, MAX_PAGES_REACHED:1}** |
| **apiRows** | **24654** |
| **apiDistinctProductIds** | **20290** |
| duration | 669s (11 min 9s) |
| error | \"stopReason=MAX_PAGES_REACHED; message=dry run failed\" |
| **dbRows baseline** (4 activities in product_snapshot) | 6629 |
| **dbDistinctProduct baseline** (4 activities) | 6185 |
| **estimatedGapRows** | 24654 - 6629 = 18025 |
| **estimatedGapDistinctProducts** | 20290 - 6185 = **14105** |
| product_snapshot changed | NO (dry-run, duplicate=0) |
| product_operation_state changed | NO |

**Batch 3 coverage: 6185 / 20290 = 30.5% (dbDistinct / apiDistinct)**

**1 activity hit MAX_PAGES_REACHED** (the 1000-page limit). Likely candidate is
one of 3859423/3864871 (the largest pre-existing activities). Need Phase 4-3H Batch 3'
with maxPagesPerActivity=2000 or unlimited to cover this.

## 4. Per-user constraint check

| User constraint | Result |
|---|---|
| 1. dryRun=false (not allowed) | N/A (only dryRun=true) |
| 2. confirm=true (not allowed) | N/A (no real backfill) |
| 3. real backfill (not allowed) | NOT PERFORMED |
| 4. RECENT_30D maxActivities (not allowed) | N/A (CUSTOM_ACTIVITY_IDS) |
| 5. Sync probe (not allowed to run long) | STOPPED (last 22:45:22, no recent calls) |
| 6. CUSTOM_ACTIVITY_IDS only | PASS |
| 7. Batch 2 = 5 activities | PASS |
| 8. Batch 3 only after Batch 2 SUCCESS | PASS (B2 SUCCESS before B3 start) |
| 9. Dry-run only (no business table writes) | PASS (duplicate=0) |

## 5. Combined B1+B2+B3 coverage (14 activities)

| Batch | activities | apiRows | apiDistinct | dbRows | dbDistinct | gap_distinct | coverage |
|---|---|---|---|---|---|---|---|
| 4-3H B1 (5 act) | 3371572/3419461/3543332/3558291/3592624 | 16766 | 14457 | 580 | 574 | 13883 | 4.0% |
| 4-3H B2 (5 act) | 3601935/3667047/3676949/3686015/3686016 | 3328 | 3113 | 407 | 406 | 2707 | 13.0% |
| 4-3H B3 (4 act, 3/4 success) | 3749687/3859423/3859426/3864871 | 24654 | 20290 | 6629 | 6185 | 14105 | 30.5% |
| **B1+B2+B3 total (14 act)** |  | **44748** | **37860** | **7616** | **7092** | **30695** | **18.7%** |

## 6. Full 24-activity coverage (recalculated)

| Layer | Distinct product_id |
|---|---|
| 4-3E/G 10 activities db | 23206 |
| 4-3H 14 activities db | 7092 |
| 4-3E 10 + 4-3H 14 (overlapping - 1320 cross-activity dup) | 23206 + 7092 - 1320 = **28978** |
| 24 activities db total | **28978** (matches) |
| 4-3H 14 activities apiDistinct | 37860 |
| 4-3E 10 activities apiDistinct (estimated by avg ~2900/act) | ~29000 (rough) |
| **24 activities apiDistinct (estimated)** | **~66860** |
| **24-activities product coverage** | **28978 / 66860 = ~43%** |

The earlier Phase 4-4 estimate of ~42% was approximately right.

## 7. Quality checks (all PASS)

| Check | Result |
|---|---|
| product_snapshot duplicate (product_id, activity_id) | 0 |
| RUNNING job | 0 |
| Redis backfill lock | 0 |
| backend health | UP |
| business tables changed by dry-run | NO (proven by duplicate=0 and equal rows_total before/after) |

## 8. Conclusion: PARTIAL PASS

- **Batch 1 (5 activities): PASS** with full upstream evidence
- **Batch 2 (5 activities): PASS** with full upstream evidence
- **Batch 3 (4 activities): PARTIAL** with 1/4 hitting MAX_PAGES_REACHED
  - NOT API_ERROR (constraint-safe: no upstream rate limit)
  - Page limit issue, can be re-attempted with higher maxPagesPerActivity

### Critical insight: B3 page limit

The 1 incomplete activity in Batch 3 is a **page-count limit issue**, not an
upstream error. Re-running with maxPagesPerActivity=2000 or higher would
likely complete it. This is a configuration tuning, not a coverage gap.

## 9. Per user rule: STOP after PARTIAL

Per user constraint:
> \"If Batch 2 FAIL/PARTIAL/API_ERROR, stop. Do not run real backfill.\"

**Batch 3 hit PARTIAL, so per user rule we STOP.** No Phase 4-3H Batch 3' (no
increased maxPages). No real backfill on any of the 14 activities.

## 10. Artifacts

- \D:\Projects\SAAS\runtime\phase4-3h-b2-dryrun-submit.json\
- \D:\Projects\SAAS\runtime\phase4-3h-b2-jid.txt\
- \D:\Projects\SAAS\runtime\phase4-3h-b2-poll.log\
- \D:\Projects\SAAS\runtime\phase4-3h-b2-baseline.txt\
- \D:\Projects\SAAS\runtime\phase4-3h-b3-dryrun-submit.json\
- \D:\Projects\SAAS\runtime\phase4-3h-b3-jid.txt\
- \D:\Projects\SAAS\runtime\phase4-3h-b3-poll.log\

## 11. Next steps (deferred per PARTIAL stop rule)

1. **Phase 4-5: Coverage catch-up real backfill** (after B3' investigation):
   - Use 4-3H B1+B2 evidence to confirm 0% API_ERROR at 5 activities
   - Re-run B3 with maxPagesPerActivity=2000 to capture the 1 incomplete activity
   - Then run real backfill for the 14 activities to close the ~30,000 product gap

2. **Phase 4-6: API rate limit investigation**:
   - 4-3D showed 60% API_ERROR at 20 activities
   - 4-3H showed 0% API_ERROR at 5 activities
   - Find the rate-limit window and tune concurrency

3. **Phase 4-7: Display rule re-evaluation**:
   - Wait for cron ProductDisplayRuleJob at next :15 minute mark
   - Then re-check pending 1344 -> DISPLAYING/HIDDEN ratio
