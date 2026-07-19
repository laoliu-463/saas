# Handover: DDD-PERF-004

## Done

- New facade interface `OrderPerformanceQueryFacade` under
  `domain/performance/facade` with 5 methods:
  - `getOrderPerformance(orderId)`
  - `batchGetOrderPerformance(orderIds)`
  - `listPerformance(query)`
  - `getPerformanceSummary(query)`
  - `exportPerformance(query)`
- `LegacyOrderPerformanceQueryFacade` implementation delegates to existing
  `PerformanceQueryService` and `PerformanceSummaryService`; converts
  `PerformanceDetailDTO` / `PerformanceListItemDTO` to `OrderPerformanceDTO`.
- `OrderPerformanceDTO` (DTO 投影) carries the spec-required fields:
  finalChannelId/Name, finalRecruiterId/Name, channelAttributionType,
  recruiterAttributionType, estimate / effective serviceProfit /
  recruiterCommission / channelCommission / grossProfit, isValid, isReversed.
- Defensive behavior: any service exception returns an empty projection; null
  orderIds returns an empty batch response. Order list / data platform callers
  will not break if a single record is missing.

## Constraints honored

- No business formula changes; this is a read-side facade.
- No new Mapper injection in performance domain.
- Permission filter still flows through the caller's
  `PerformanceAccessContext` (which already wraps `UserDomainFacade` data
  scope).

## Next

- **Wiring ticket**: route `OrderController.getOrderDetail` and
  `OrderService.enrichOrderList` through
  `OrderPerformanceQueryFacade.getOrderPerformance` /
  `batchGetOrderPerformance`.
- **DDD-PERF-001**: extract `PerformanceCalculationApplicationService` (still
  open).
- **DDD-PERF-005**: extract `ExclusiveMerchantApplicationService` (still open).

## Risk

- `mvn clean test` baseline failures are pre-existing; not caused by this task.
- `OrderPerformanceDTO` does NOT include `service_fee_expense` columns yet —
  the new SQL migration in `migrate-all.sql` is staged separately. Decide
  whether to backfill those columns into the DTO in a follow-up commit.
- Wiring is intentionally NOT done in this commit to keep the diff small and
  avoid double edits on `OrderController`.

## Report

`harness/reports/ddd-perf-004-2026-06-12.md`