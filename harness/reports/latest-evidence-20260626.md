# latest evidence 2026-06-26

## Scope
- Task: ORDER-PERFORMANCE-BACKFILL-001 status verification.
- Env: local real-pre.
- Type: docs/state update after read-only SQL evidence.

## Boundary
- Primary domain: performance.
- Related domains: order, analytics.
- Writes executed: none to business tables.
- Backfill API executed: no.

## Evidence

Read-only anti-join check:

```text
orders_total              = 342088
performance_records_total = 342088
missing_total             = 0
```

Read-only stale invalidated check:

```text
stale_invalidated_count = 0
```

Read-only service-fee expense checks:

```text
effective_service_fee_expense_mismatch_count = 0
active_estimate_expense_mismatch_count       = 0
```

Observed 6 estimate expense mismatches only on cancelled orders:

```text
order_status = 4
is_valid     = false
is_reversed  = true
estimate_service_profit   = 0
effective_service_profit  = 0
```

Code evidence: `PerformanceCalculationService.zeroCommissions` explicitly zeroes service fee expense and profit fields for reversed orders, so those 6 rows are not a repair target.

## Conclusion

`ORDER-PERFORMANCE-BACKFILL-001` does not need a write repair on local real-pre at this point. The previous `missing_performance=15` state was stale; current anti-join is 0. Next DDD work should move to Y-1 performance-domain inventory and A-11/A-12 dashboard/export verification.

## Remaining Risk

- This report covers local real-pre only.
- Remote real-pre was not queried or deployed.
- No business API/E2E was executed in this docs/state verification slice.
