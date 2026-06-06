# Evidence Report: service fee income and expense source fix

## Metadata

- Time: 2026-06-06 21:31:10 +08:00
- Environment: local `real-pre`
- Branch: `feature/auth-system`
- Code commit: `bc964b5f fix: align service fee income and expense sources`
- Retro commit: `40af123b docs(harness): add service fee fix retro`
- Deploy remote: false
- Scope: full

## Problem

The order/data dashboard money fields had two observable risks:

- service fee expense was being inferred from `income - tech fee - profit` when DB expense was 0.
- a draft/runtime parser path summed `colonel_order_info.estimated_commission` and `colonel_order_info_second.estimated_commission`, which over-counted two dual-institution orders by CNY 1.90 in the frozen window.

## Frozen Window Evidence

Window:

```text
create_time >= 2026-06-06 00:00:00
create_time <= 2026-06-06 20:23:08
```

API evidence:

```text
GET /api/orders?page=1&size=1&startTime=2026-06-06 00:00:00&endTime=2026-06-06 20:23:08&timeField=createTime
ordersTotal=7670

GET /api/orders/stats?startTime=2026-06-06 00:00:00&endTime=2026-06-06 20:23:08&timeField=createTime
statsTotalOrders=7670
```

SQL evidence after runtime deployment:

```text
orders=7670
current_stored_income_yuan=3058.57
expected_income_yuan=3056.67
residual_overcount_yuan=1.90
residual_mismatched_orders=2
```

Raw field relationship before code fix:

```text
stored=first_raw:    6956 orders, CNY 2579.03
stored=second_raw:    712 orders, CNY 475.24
stored=first+second:   2 orders, CNY 4.30; overcount CNY 1.90
```

Inference:

- `colonel_order_info.estimated_commission` is the primary service fee income field.
- `colonel_order_info_second.estimated_commission` is a fallback when primary commission is missing.
- When both fields exist, summing them double-counts service fee income.
- No raw `service_fee_expense` / `estimated_service_fee_expense` field was found in the current order payload sample; DB expense remains 0.

## Code Changes

- `OrderDualTrackAmountResolver`: changed service fee income resolution from summing institution fields to first-positive priority: top-level, `colonel_order_info`, then `colonel_order_info_second`.
- `PerformanceSummaryService`: service fee expense now comes directly from DB aggregate, without reverse inference.
- `DataApplicationService`: dashboard metrics and order summary service fee expense now use DB aggregate directly.
- `frontend/src/views/data/index.vue`: frontend displays backend `serviceFeeExpense` directly; missing value displays 0 instead of re-inferring.

## Verification

Targeted backend tests:

```text
mvn -f backend/pom.xml "-Dtest=OrderDualTrackAmountResolverTest,PerformanceSummaryServiceTest" test
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0

mvn -f backend/pom.xml "-Dtest=DataControllerTest,DataApplicationServiceOrderSummaryCacheTest,PerformanceMetricsQueryServiceTest,OrderDualTrackAmountResolverTest,PerformanceSummaryServiceTest" test
Tests run: 67, Failures: 0, Errors: 0, Skipped: 0
```

Frontend targeted test:

```text
npm run test -- --run src/views/data/index.test.ts
2 tests passed
```

Additional frontend probe:

```text
npm run test -- --run src/views/data/index.test.ts src/views/data/OrderList.test.ts
index.test.ts passed
OrderList.test.ts had 2 failures in existing export permission/detail export assertions; not caused by the changed data index component.
```

Harness full run:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote false -Message "fix: align service fee income and expense sources"
Backend package: PASS
Frontend build: PASS
Docker restart: PASS
Local health verification: PASS
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
Evidence: harness/reports/evidence-20260606-212835.md
Retro: harness/reports/retro-20260606-212859.md
```

Post-run health:

```text
backend /api/system/health = {"status":"UP"}
frontend /healthz = ok
real-pre backend/frontend/postgres/redis containers = healthy
```

Runtime API check:

```text
GET /api/dashboard/metrics
estimate.serviceFeeExpense=0.0
estimate.metricsSource=performance_records
settle.serviceFeeExpense=0.0
```

## Conclusion

`PARTIAL`

Code, build, local deployment, health checks, and preflight validation passed. The root code paths that inferred service fee expense and double-counted dual-institution service fee income have been corrected.

Historical `real-pre` data for the frozen window still contains 2 over-counted rows totaling CNY 1.90. This report does not mark the 7670-order historical reconciliation as fully PASS until those rows are reprocessed through a controlled sync/repair path and `performance_records` is refreshed.

## Remaining Risk

- Historical data repair is pending for the two over-counted orders.
- `/api/data/orders/summary` only accepts date-level filters, so it cannot reproduce the exact `20:23:08` frozen window; second-level reconciliation currently requires SQL or `/api/orders`.
- `OrderList.test.ts` has two export-related failures outside this task scope and should be triaged separately if it is part of the required frontend regression set.
