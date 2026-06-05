# Retro Summary - ORDER-PERFORMANCE-MISSING-AUDIT-001

- Time: 2026-06-05 16:12:45 CST
- Scope: readonly audit / reports only
- Conclusion: no Harness code upgrade performed

## What Worked

- The audit waited for a real scheduler cycle instead of relying on a static anti-join count.
- That exposed the recurring event race: new orders were persisted successfully, but async performance listeners still logged `order not found`.
- Source, DB and logs aligned on one explanation.

## What Was Missing

- The original 58 missing-order set was not snapshotted as order IDs before later syncs changed the DB state.
- Order-synced events are not written to `domain_event_outbox`, so there is no persistent replay evidence.
- `PerformanceRecordSyncListener` only logs skipped order-not-found cases; it does not record retry/dead-letter state.

## Suggested Harness Improvements

1. Add a reusable SQL evidence script for order-performance anti-join snapshots.
2. Add a real-pre detector for `Performance calculation skipped, order not found`.
3. Add a post-sync assertion: every inserted/updated order in a sync window should have a `performance_records` row after a bounded delay.
4. Add an evidence template section for "dynamic count changed during audit" so reports do not overstate stale counts.

## Non-Actions

- Did not update source code.
- Did not run backfill.
- Did not change dashboard formulas.
- Did not deploy remote real-pre.
