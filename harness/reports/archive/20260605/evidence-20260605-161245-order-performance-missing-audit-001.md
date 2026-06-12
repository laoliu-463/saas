# Evidence Report - ORDER-PERFORMANCE-MISSING-AUDIT-001

- Time: 2026-06-05 16:12:45 CST
- Env: local real-pre
- Scope: readonly audit / reports only
- Branch: feature/auth-system
- Commit before report: 5268fdc7
- Worktree clean: no, pre-existing dirty remains
- Remote deployed: no
- Conclusion: FAIL_EVENT_RACE + MISSING_BACKFILL

## Worktree State

Pre-existing dirty files were present before this task and were not staged or modified:

- `frontend/src/views/data/index.test.ts`
- `frontend/src/views/data/index.vue`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md`
- `harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md`
- `harness/reports/order-field-mapping-audit-001-20260605-142932.md`

This task adds only the audit report, evidence report and retro summary.

## Safety / Health

Safety command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

Result: PASS.

Health:

- backend `/api/system/health`: `{"status":"UP"}`.
- backend, frontend, PostgreSQL, Redis real-pre containers healthy.

## Preflight

Command:

```powershell
npm run e2e:real-pre:p0:preflight
```

Evidence directory:

```text
runtime/qa/out/real-pre-preflight-20260605-161216
```

Result: PASS.

## Current Missing Count

SQL anti-join:

```sql
select count(*) as missing_count
from colonelsettlement_order o
left join performance_records pr on pr.order_id = o.order_id
where o.deleted = 0
  and pr.id is null;
```

Observed counts during audit:

- 16:03 CST: 14.
- 16:11 CST: 17.

The earlier reconnect report observed 58, but the 58-order list was not snapshotted before the database changed.

## Group Counts at 16:11 CST

| Metric | Count |
| --- | ---: |
| missing_total | 17 |
| missing_update_0810 | 7 |
| missing_update_0800 | 0 |
| missing_update_0430_or_0350 | 10 |
| missing_channel_null | 17 |
| missing_recruiter_null | 16 |
| missing_estimate_service_fee_zero | 1 |
| missing_order_status_4 | 1 |

## Log Evidence

Since 2026-06-05 15:45 CST:

- `Performance calculation skipped, order not found`: 59.
- `Performance calculation failed`: 0.
- `isv.signature-invalid` / `signature-invalid` / `code=40003`: 0.

16:10 CST scheduler evidence:

- `ORDER_SYNC_INSTITUTE ... fetched=100 inserted=7 updated=93 failed=0`.
- Seven order-not-found skips were logged around the same sync window.

The seven current missing orders with direct skip logs:

- `6953438238233139029`
- `6953438332631127218`
- `6953440258866681497`
- `6953444592578991243`
- `6953444604278609547`
- `6953444647723734099`
- `6953444662847608788`

## Source Evidence

`OrderSyncPersistenceService`:

- line 106: `persistOrder` has `@Transactional`.
- line 159: publishes `new OrderSyncedEvent(...)`.

`PerformanceRecordSyncListener`:

- line 76: `@Async`.
- line 77: `@EventListener`.
- line 86: logs `Performance calculation skipped, order not found`.
- line 90: calls `performanceCalculationService.upsertFromOrder(order)` only if the order query succeeded.

`PerformanceCalculationService`:

- line 64: only skips null order or blank orderId.
- line 128: status filtering marks reversed but does not skip insert.
- line 183: reversed commissions are zeroed.

## Event Persistence Evidence

Existing event tables:

- `domain_event_outbox`
- `domain_event_consume_log`

Current missing order IDs:

- outbox payload hits: 0.
- consume-log payload hits: 0.
- consume-log total rows: 0.

Conclusion: order-synced events are not persistently replayable from existing event tables.

## Impact

Missing `performance_records` affects:

- performance/order detail joins
- dashboard performance totals
- downstream effective/estimate performance metrics
- any validation that treats orders and performance as fully covered

It does not prove order field mapping is wrong.

## Final Evidence Conclusion

`FAIL_EVENT_RACE` is directly proven for the seven current orders that have `order not found` logs and no performance records.

`MISSING_BACKFILL` remains for ten older orders that are missing performance records but whose original logs are unavailable after backend container recreation.

Recommended next tasks:

- `ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001`
- `ORDER-PERFORMANCE-BACKFILL-001`

No code was modified and no backfill was executed in this audit.
