# Agent Lock: DDD-ORDER-004

| Field | Value |
| --- | --- |
| task_id | DDD-ORDER-004 |
| owner | Order Agent |
| branch | feature/ddd/DDD-ORDER-004 |
| status | COMPLETED |
| started | 2026-06-12T16:52:00+08:00 |
| completed | 2026-06-12T17:25:00+08:00 |

## Scope

Extract default-only order attribution into `OrderDefaultAttributionPolicy` +
`OrderDefaultAttributionResolver` + `OrderAttributionInput` +
`OrderDefaultAttributionResult` + `OrderPickSourceMappingAdapter`. Route via
`OrderAttributionRouter` when `ddd.refactor.enabled=true` and
`ddd.refactor.order-attribution.enabled=true`.

## Out of scope

- Performance exclusive merchant/talent application
- Final attribution / commission / gross profit
- Order list BFF (handled by DDD-PERF-004)

## Targeted tests

```
mvn -Dtest=OrderDefaultAttributionPolicyTest,OrderDefaultAttributionResolverTest,OrderAttributionRouterTest,OrderSyncServiceTest,LegacyProductDomainFacadeTest,DddCrossDomainMapperGuardTest,DddSlimOrder002RoutingTest test
```

Result: 74 tests run, 0 failures, 0 errors, 1 skipped (archunit legacy guard).

## Baseline risk (carried forward)

`mvn clean test` had pre-existing baseline failures (NoClassDefFound on
non-ORDER classes, Spring context failures in other mapper/config tests) that
are unrelated to this task. See
`harness/reports/ddd-order-004-2026-06-12.md` for triage notes. Must be
addressed before entering CLEAN / VERIFY phase.