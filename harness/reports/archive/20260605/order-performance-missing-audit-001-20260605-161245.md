# ORDER-PERFORMANCE-MISSING-AUDIT-001

- Time: 2026-06-05 16:12:45 CST
- Env: local real-pre
- Scope: readonly audit / reports only
- Branch: feature/auth-system
- Commit before report: 5268fdc7
- Conclusion: FAIL_EVENT_RACE + MISSING_BACKFILL

## 1. Problem Restatement

After Douyin upstream signing recovered, order sync resumed. The next problem moved downstream:

- Local real-pre upstream: PASS.
- 6468 / 2704 / activity product upstream APIs: PASS.
- Order sync: PASS at upstream and order-table persistence layer.
- Order to `performance_records`: FAIL / PARTIAL.

Previous reconnect evidence observed 58 orders without `performance_records`. During this audit the number changed:

- 2026-06-05 16:03 CST: current missing count was 14.
- 2026-06-05 16:11 CST after the 16:10 order sync window: current missing count was 17.

Important: the original 58-order set was not snapshotted as a per-order list before the database changed. This report therefore does not fabricate a 58-order list. It records the current reproducible missing set and the evidence explaining why the count moves.

## 2. Feedback Loop

Readonly feedback loop used for this audit:

1. Safety check and health check.
2. Real-pre preflight.
3. Source trace for order event production and performance event consumption.
4. SQL anti-join: `colonelsettlement_order` left join `performance_records`.
5. Backend log scan for `Performance calculation skipped, order not found`.
6. Event/outbox table inspection.
7. One full 10-minute scheduler observation window.

No business code, SQL migration, env, Docker compose file, or dashboard formula was modified.

## 3. Source Evidence

### 3.1 Event Production Runs Inside Order Transaction

`backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java`:

- line 106: `persistOrder` is `@Transactional`.
- lines 118, 137, 141: `publishOrderSynced(...)` is called before method return.
- line 159: `eventPublisher.publishEvent(new OrderSyncedEvent(...))`.

Inference: order-synced events are published while the order persistence transaction is still active.

### 3.2 Performance Consumer Is Async EventListener

`backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java`:

- line 76: `@Async`.
- line 77: `@EventListener`.
- line 84: listener queries `orderMapper.findByOrderId(event.orderId())`.
- line 86: if order is not found, it logs `Performance calculation skipped, order not found`.
- line 90: only then calls `performanceCalculationService.upsertFromOrder(order)`.

Inference: async listener can run on another thread before the transaction that inserted the order has committed. If it does, it cannot read the row and exits without retry.

### 3.3 Calculation Service Does Not Filter These Orders Out

`backend/src/main/java/com/colonel/saas/service/PerformanceCalculationService.java`:

- line 64: `upsertFromOrder` only returns null when order is null or `orderId` is empty.
- line 128: cancelled/invalid status is mapped to `reversed`.
- line 142 and line 183: reversed orders still build a record, but commission/profit fields are zeroed.

`backend/src/main/java/com/colonel/saas/service/OrderCommissionPolicy.java`:

- line 56: only status 4 is not counted toward performance, but it should still create a reversed performance record.

Inference: null channel, null recruiter, status 4, or zero service fee are not sufficient reasons for missing `performance_records`.

## 4. Runtime Evidence

### 4.1 Safety / Health / Preflight

Safety check:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

Result: PASS.

Runtime health:

- backend `/api/system/health`: `{"status":"UP"}`.
- real-pre containers: backend, frontend, PostgreSQL, Redis healthy.

Preflight:

```powershell
npm run e2e:real-pre:p0:preflight
```

Evidence directory:

```text
runtime/qa/out/real-pre-preflight-20260605-161216
```

Result: PASS.

### 4.2 Signature Failure Is Not Current Cause

Backend logs since 2026-06-05 15:45 CST:

- `isv.signature-invalid` / `signature-invalid` / `code=40003`: 0.

This audit does not reopen the signature issue.

### 4.3 Event Race Reproduced in Logs

Backend logs since 2026-06-05 15:45 CST:

- `Performance calculation skipped, order not found`: 59.
- `Performance calculation failed`: 0.

Observed scheduler windows:

- 2026-06-05 16:00 CST: `ORDER_SYNC_INSTITUTE ... fetched=100 inserted=7 updated=93 failed=0`.
- 2026-06-05 16:10 CST: `ORDER_SYNC_INSTITUTE ... fetched=100 inserted=7 updated=93 failed=0`.

At 2026-06-05 16:10 CST, seven new `order not found` warnings appeared immediately before the sync summary:

- `6953444662847608788`
- `6953444647723734099`
- `6953444604278609547`
- `6953444592578991243`
- `6953440258866681497`
- `6953438332631127218`
- `6953438238233139029`

All seven exist in `colonelsettlement_order` after the sync and are still missing `performance_records` at 16:11 CST.

## 5. Current Missing Order List

Current query time: 2026-06-05 16:11 CST.

All 17 current missing orders:

| order_id | pay_time | status | order_amount | estimate_service_fee | channel_user_id | colonel_user_id | attribution | product_id | activity_id | update_time | direct order-not-found log |
| --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- | --- | --- |
| 6953438238233139029 | 2026-06-05 16:08:08 | 1 | 899 | 27 | null | null | COLONEL_MAPPING_NOT_FOUND | 3750956293179965772 | 3223881 | 2026-06-05 08:10:00 UTC | yes |
| 6953438332631127218 | 2026-06-05 16:04:12 | 1 | 1990 | 60 | null | null | COLONEL_MAPPING_NOT_FOUND | 3784760236901007881 | 3592624 | 2026-06-05 08:10:00 UTC | yes |
| 6953440258866681497 | 2026-06-05 16:04:32 | 1 | 1680 | 17 | null | null | COLONEL_MAPPING_NOT_FOUND | 3775838258387353914 | 3859426 | 2026-06-05 08:10:00 UTC | yes |
| 6953444592578991243 | 2026-06-05 16:00:13 | 1 | 2990 | 90 | null | null | COLONEL_MAPPING_NOT_FOUND | 3628139758385003203 | 3176208 | 2026-06-05 08:10:00 UTC | yes |
| 6953444604278609547 | 2026-06-05 16:05:51 | 1 | 2990 | 60 | null | null | COLONEL_MAPPING_NOT_FOUND | 3811979912072528269 | 3859423 | 2026-06-05 08:10:00 UTC | yes |
| 6953444647723734099 | 2026-06-05 16:08:49 | 1 | 860 | 9 | null | null | COLONEL_MAPPING_NOT_FOUND | 3675113136274407575 | 3272470 | 2026-06-05 08:10:00 UTC | yes |
| 6953444662847608788 | 2026-06-05 16:08:11 | 1 | 1873 | 19 | null | null | COLONEL_MAPPING_NOT_FOUND | 3781613383091093723 | 3223881 | 2026-06-05 08:10:00 UTC | yes |
| 6953433051934365549 | 2026-06-05 11:56:14 | 1 | 2390 | 72 | null | null | COLONEL_MAPPING_NOT_FOUND | 3812188308365246883 | 3272470 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953433058201311013 | 2026-06-05 11:56:40 | 1 | 990 | 20 | null | null | COLONEL_MAPPING_NOT_FOUND | 3712939976305017691 | 3859426 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953433058605078208 | 2026-06-05 12:08:12 | 1 | 850 | 0 | null | null | COLONEL_MAPPING_NOT_FOUND | 3708859506176950278 | 3859423 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953433069081663231 | 2026-06-05 12:15:20 | 1 | 1990 | 20 | null | null | COLONEL_MAPPING_NOT_FOUND | 3625942219132911176 | 3676949 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953433130388887346 | 2026-06-05 12:24:28 | 1 | 1990 | 60 | null | null | COLONEL_MAPPING_NOT_FOUND | 3784760236901007881 | 3592624 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953437860430878314 | 2026-06-05 11:56:36 | 4 | 3990 | 80 | null | 60c5d9fb-cbf7-460e-8043-776a2982a7ed | COLONEL_MAPPING_NOT_FOUND | 3809972562562253270 | 3916506 | 2026-06-05 04:30:43 UTC | no current-container log |
| 6953431246090671810 | 2026-06-05 11:47:44 | 1 | 990 | 20 | null | null | COLONEL_MAPPING_NOT_FOUND | 3712939976305017691 | 3859426 | 2026-06-05 03:50:00 UTC | no current-container log |
| 6953431256633513748 | 2026-06-05 11:43:48 | 1 | 790 | 8 | null | null | COLONEL_MAPPING_NOT_FOUND | 3821840547308503410 | 3859423 | 2026-06-05 03:50:00 UTC | no current-container log |
| 6953431268316550663 | 2026-06-05 11:44:29 | 1 | 759 | 8 | null | null | COLONEL_MAPPING_NOT_FOUND | 3814411819418779887 | 3558291 | 2026-06-05 03:50:00 UTC | no current-container log |
| 6953431288217933446 | 2026-06-05 11:45:57 | 1 | 1990 | 20 | null | null | COLONEL_MAPPING_NOT_FOUND | 3794907286057648478 | 3859423 | 2026-06-05 03:50:00 UTC | no current-container log |

## 6. Grouped Classification

### A. Direct Event Race Evidence: 7 Orders

Orders:

- `6953438238233139029`
- `6953438332631127218`
- `6953440258866681497`
- `6953444592578991243`
- `6953444604278609547`
- `6953444647723734099`
- `6953444662847608788`

Evidence:

- Each has `Performance calculation skipped, order not found` in current backend logs.
- Each exists in `colonelsettlement_order` after the sync.
- Each has no `performance_records`.
- Sync summary reports `failed=0`, so order sync itself did not fail.
- `Performance calculation failed` count is 0, so no calculation exception was recorded.

Conclusion for this group: `FAIL_EVENT_RACE`.

### B. Historical Missing Backfill: 10 Orders

Orders:

- `6953433051934365549`
- `6953433058201311013`
- `6953433058605078208`
- `6953433069081663231`
- `6953433130388887346`
- `6953437860430878314`
- `6953431246090671810`
- `6953431256633513748`
- `6953431268316550663`
- `6953431288217933446`

Evidence:

- These orders were last updated in earlier sync windows: 11:50 CST and 12:30 CST.
- The backend container was later recreated during upstream secret reload, so current container logs cannot prove their original listener outcome.
- They have the same data shape as the directly proven race group: order exists, raw extra data exists, no performance record.
- There are no outbox or consume-log records for these order IDs.

Conclusion for this group: `MISSING_BACKFILL`, likely caused by the same non-persistent async event race, but direct historical log evidence is unavailable after container recreation.

### C. Not Supported as Primary Cause

These hypotheses are not supported by current evidence:

- `default_channel_id` / channel null: all 17 are channel-null, but calculation maps this to `unattributed` and should still insert a record.
- recruiter null: 16 are recruiter-null, but calculation supports null recruiter and should still insert a record.
- order status filtering: one missing order is status 4; code should create a reversed record with zeroed commissions.
- estimate service fee 0: one missing order has estimate_service_fee=0; code still builds a record.
- raw payload missing: all 17 have `extra_data`.
- upstream still failing: signature-invalid count is 0 after recovery.

## 7. Event Table Evidence

Tables exist:

- `domain_event_outbox`
- `domain_event_consume_log`

But for the current missing orders:

- `domain_event_outbox` payload hits: 0.
- `domain_event_consume_log` hits: 0.
- `domain_event_consume_log` total rows: 0.

Observed outbox event types are product/sample events, not order-synced events.

Conclusion: current order-to-performance path is not backed by persistent event delivery or replay. It relies on Spring local events plus logs.

## 8. Stage Conclusion

Current most credible root cause:

```text
FAIL_EVENT_RACE:
OrderSyncPersistenceService publishes OrderSyncedEvent inside an active transaction.
PerformanceRecordSyncListener consumes it asynchronously with @Async @EventListener.
The async listener can query before the order transaction commits.
When that happens, it logs "order not found" and exits without retry.
The order later exists in colonelsettlement_order but has no performance_records.
```

There is also residual historical damage:

```text
MISSING_BACKFILL:
Orders already skipped by this race need explicit performance backfill.
Later sync windows can refill some records, but the mechanism is not reliable and keeps producing new misses.
```

This is not an order field mapping issue and not a dashboard formula issue.

## 9. Recommended Fix Path

### Temporary Containment

- Do not declare order-to-performance closed.
- Do not run dashboard/settlement final validation using current `performance_records`.
- If business needs immediate dashboard accuracy, run an explicit, auditable performance backfill for the current missing order IDs after operator approval.

### Root Cause Fix

Create `ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001`:

- Change order-synced performance consumption to run after the order transaction commits.
- Prefer `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` while preserving async execution if needed.
- Add a regression test proving the listener does not query the order before commit.
- Add failure visibility for order-not-found skip cases, ideally with retry or a persistent event/retry table.

### Backfill

Create `ORDER-PERFORMANCE-BACKFILL-001`:

- Use the existing admin backfill endpoint or service path with explicit order IDs or `onlyMissing=true`.
- Verify `scanned`, `upserted`, `failed`.
- Re-run the anti-join count after backfill.
- Re-check dashboard/order detail amounts after backfill, because missing performance records affect downstream performance/dashboard totals.

### Long-Term Governance

- Persist order-synced events to outbox or a consume log.
- Add dead-letter/retry evidence for `PerformanceRecordSyncListener`.
- Add a periodic missing-performance detector with alerting.

## 10. Non-Actions

- Did not modify code.
- Did not run performance backfill.
- Did not clear database.
- Did not change dashboard formulas.
- Did not alter order field mapping.
- Did not deploy remote real-pre.
