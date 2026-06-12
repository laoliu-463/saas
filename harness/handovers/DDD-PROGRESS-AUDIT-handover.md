# Handover: DDD-PROGRESS-AUDIT

## Summary

Calibrated real DDD progress against the 53-task pool using code, commits, tests, and harness artifacts. Board TOTAL 37/53 and user-reported 33/53 both over-count tasks that only have partial implementation or mislabeled completions.

## Evidence-based totals

| Metric | Count |
| --- | --- |
| Strict DONE (commit + code + tests match intent) | **31/53 (58%)** |
| PARTIAL (code exists, spec gap) | **10** |
| TODO (no landing evidence) | **12** |

## Immediate next work (Sprint 1 P0)

1. **DDD-ORDER-004** — Replace SLIM field-writer with true default-only attribution (no exclusive rules in order sync path when switch on).
2. **DDD-PERF-004** — Route order list / data platform performance enrichment through `PerformanceQueryFacade`; add `OrderPerformanceDTO` batch API.
3. **DDD-PRODUCT-004** — Cherry-pick / finish `feature/ddd/SPRINT-1-P0` commits (`fef02b1d`) for full CopyPromotion + DouyinConvertPort; current branch has thin wrapper only.
4. **DDD-PRODUCT-005** — **SKIP** (commit `0498b08e`, `SampleApplicationPort` landed).

## Blockers for CLEAN

- Cross-domain mappers remain in sample (`ProductMapper`, `TalentMapper`) and data BFF (`PerformanceRecordMapper` direct).
- `AttributionService` still runs exclusive merchant/talent in order sync legacy path.
- `ProductPinPolicy`, `PerformanceAttributionPolicy`, `ExclusiveTalentApplicationService`, `ExclusiveMerchantApplicationService` not extracted.
- Facade/Policy/ApplicationService/QueryService not all stable.

## WIP on workspace (do not mix into audit commit)

- Branch was `feature/ddd/DDD-SAMPLE-002-eligibility-policy` at `ea7763bb`.
- Uncommitted PRODUCT-004 WIP + ProductService test edits + e2e changes.

## Report

`harness/reports/ddd-progress-audit-2026-06-12.md`
