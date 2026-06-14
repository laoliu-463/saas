# Product Library Count Phase 2 Dry-Run Pagination

- Time: 2026-06-14 21:07:19
- Env: real-pre local
- BaseUrl: http://127.0.0.1:8081/api
- ActivityId: 3859423
- Scope: ACTIVE_ONLY
- MaxActivities: 50
- PageSize/MaxPages: 20/300

## Read-Only Guard
| Metric | Before | After |
| --- | ---: | ---: |
| product_snapshot deleted=0 | 7962 | 7962 |
| product_operation_state deleted=0 | 7962 | 7962 |
| /api/products total | 2956 | 2956 |

## Deep Dry-Run
| Metric | Value |
| --- | ---: |
| dryRun | True |
| pagesFetched | 201 |
| totalFetchedRows | 4009 |
| distinctProductIds | 4008 |
| currentDbRowsForActivity | 2000 |
| estimatedGapRows | 2009 |
| expectedMissingRowsIfCurrentMax100 | 2009 |
| stoppedReason | DONE_NO_MORE |
| stillHasNextWhenStopped | False |

## Full Dry-Run
| Metric | Value |
| --- | ---: |
| dryRun | True |
| activitiesScanned | 5 |
| activitiesWithProducts | 5 |
| activitiesReachedMaxPages | 0 |
| activitiesStillHasNextAfterMaxPages | 0 |
| apiFetchedRows | 2367 |
| apiDistinctProductIds | 2004 |
| dbRowsForScannedActivities | 2360 |
| estimatedGapRows | 7 |
| apiErrors | 0 |

## Top Gap Activities
| ActivityId | Fetched | DbRows | Gap | StopReason |
| --- | ---: | ---: | ---: | --- |
| 3916506 | 1132 | 1125 | 7 | DONE_NO_MORE |
| 3929906 | 191 | 191 | 0 | DONE_NO_MORE |
| 3920684 | 410 | 410 | 0 | DONE_NO_MORE |
| 3891192 | 252 | 252 | 0 | DONE_NO_MORE |
| 3929905 | 382 | 382 | 0 | DONE_NO_MORE |

## Conclusion

- Result: PASS
- Phase 3 real backfill was not executed.
