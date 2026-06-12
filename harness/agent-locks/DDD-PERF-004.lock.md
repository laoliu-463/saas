# Agent Lock: DDD-PERF-004

| Field | Value |
| --- | --- |
| task_id | DDD-PERF-004 |
| owner | Performance Agent |
| branch | feature/ddd/DDD-PERF-004 |
| status | COMPLETED |
| started | 2026-06-12T17:35:00+08:00 |
| completed | 2026-06-12T17:50:00+08:00 |

## Scope

Add `OrderPerformanceQueryFacade` to expose performance enrichment (final
channel, final recruiter, service profit, recruiter/channel commission, gross
profit, validity) to order list / order detail / data-platform consumers via a
slim `OrderPerformanceDTO` projection.

## Out of scope

- Performance calculation changes (DDD-PERF-001 still open)
- Order list / detail controller rewiring (future wiring ticket)
- Exclusive merchant / talent application (DDD-PERF-005)

## Targeted tests

```
mvn -Dtest=LegacyOrderPerformanceQueryFacadeTest test
```

Result: 8 tests, 0 failures, 0 errors, 0 skipped.

Coverage:

- field mapping `PerformanceDetailDTO` → `OrderPerformanceDTO`
- field mapping `PerformanceListItemDTO` → `OrderPerformanceDTO`
- empty / null orderId handling
- service exception → empty result (no break of order list)
- batch delegation to `PerformanceQueryService.batchGetPerformance`
- summary delegation to `PerformanceSummaryService.getSummary`

## Baseline risk (carried forward)

Same as DDD-ORDER-004 / DDD-PRODUCT-004 — `mvn clean test` has pre-existing
baseline failures outside this task's scope. Must be triaged before CLEAN /
VERIFY.