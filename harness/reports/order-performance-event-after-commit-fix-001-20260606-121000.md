# ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001

## 1. Task

- Time: 2026-06-06 12:10 +08:00
- Env: local `real-pre`
- Scope: backend
- Selected Gate: Gate 4, because the defect affects order, performance, and dashboard downstream visibility.
- Remote deploy: not requested, not executed.

## 2. Phenomenon

Orders were persisted successfully, but some orders never produced `performance_records`.

Confirmed root-cause pattern:

```text
FAIL_EVENT_BEFORE_COMMIT + MISSING_BACKFILL
```

The order sync transaction published `OrderSyncedEvent` before commit. `PerformanceRecordSyncListener` is asynchronous and immediately queried `colonelsettlement_order`; when the row was not yet visible, it logged `Performance calculation skipped, order not found` and returned without retry/dead-letter/backfill.

## 3. Boundary Check

- Order domain remains responsible for persisting order facts and publishing the order synced fact event.
- Performance domain remains responsible for reading order facts and writing `performance_records`.
- This task did not move commission, final attribution, or performance write logic into `OrderSyncPersistenceService`.
- Backfill was intentionally not mixed into this task.

## 4. Changes

- `backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java`
  - Build the existing `OrderSyncedEvent` payload unchanged.
  - If transaction synchronization is active, register `TransactionSynchronization.afterCommit()` and publish only after commit.
  - If no transaction synchronization is active, keep the existing immediate publish behavior for non-transactional callers/tests.

- `backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java`
  - Added regression test proving `persistOrder()` does not publish while transaction synchronization is active and does publish after `afterCommit()`.
  - RED verified before production change: `NoInteractionsWanted`, pointing to `OrderSyncPersistenceService.publishOrderSynced`.

- `backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java`
  - Added listener tests for normal upsert + calculated event publish.
  - Added duplicate event coverage proving duplicate events use the same `upsertFromOrder` path.
  - Added missing order guard coverage proving the listener does not write performance when the order is genuinely unavailable.

## 5. Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Safety check | PASS | `safety-check.ps1 -Env real-pre -Scope backend` |
| RED test | PASS as expected failure | `persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit` failed before production change because `publishEvent` was called inside the transaction |
| Targeted tests | PASS | `mvn -f .\backend\pom.xml "-Dtest=OrderSyncPersistenceServiceTest,PerformanceRecordSyncListenerTest,PerformanceCalculationServiceTest,PerformanceBackfillServiceTest" test` => 16 tests, 0 failures |
| Full backend tests | PASS | `mvn -f .\backend\pom.xml test` => 1730 tests, 0 failures, 0 errors |
| Backend package | PASS | `mvn -f .\backend\pom.xml -DskipTests package` => BUILD SUCCESS |
| Container reload | PASS | `restart-compose.ps1 -Env real-pre -Scope backend`; `backend-real-pre` rebuilt/recreated |
| Health | PASS | `verify-local.ps1 -Env real-pre -Scope backend`; `/api/system/health={"status":"UP"}` |
| real-pre preflight | PASS | `runtime/qa/out/real-pre-preflight-20260606-120648/report.md` |
| SQL anti-join | PARTIAL | `orders_total=1350`, `performance_total=1335`, `missing_performance=15` |
| Duplicate performance rows | PASS | `duplicate_performance_order_ids=0` |
| Runtime logs | PASS | Backend logs since restart contained no `Performance calculation skipped`, `Performance calculation failed`, or `order not found` matches |

## 6. Evidence Paths

- Harness evidence: `harness/reports/evidence-20260606-120829.md`
- Final evidence after state sync: `harness/reports/evidence-20260606-121045.md`
- Retro: `harness/reports/retro-20260606-120843.md`
- Content maintenance plan: `harness/reports/content-retire-20260606-120813.md`
- Preflight: `runtime/qa/out/real-pre-preflight-20260606-120648/report.md`

## 7. Status

`PARTIAL`

Reason:

- Root-cause event timing fix is implemented and locally verified.
- Existing historical gap remains: `missing_performance=15`.
- `ORDER-PERFORMANCE-BACKFILL-001` is still required.
- Remote real-pre deploy was not requested.
- Git commit/push was not executed because the worktree contains pre-existing unrelated frontend/report dirty files and `agent-do.ps1` would stage all changed files via `git-push-safe.ps1`.

## 8. Next Task

`ORDER-PERFORMANCE-BACKFILL-001`

Recommended validation:

```sql
SELECT COUNT(*) AS missing_performance
FROM colonelsettlement_order o
LEFT JOIN performance_records pr ON pr.order_id = o.order_id
WHERE o.deleted = 0
  AND pr.order_id IS NULL;
```

Backfill must use an explicit repair/backfill entry point, not manual `INSERT INTO performance_records`.
