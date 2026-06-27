# Retro: DDD-COMPLETE-100-PRODUCT #96 Partial

## What Changed

- Reconciled GitHub product sub-issue state for #130-#136.
- Reopened #135 after real-pre read-only SQL showed zero `colonelsettlement_order.pick_source` rows.
- Reopened #96 because #135 is a child of the product epic and still requires a real order sample.
- Updated `harness/engineering/issues-index.md` and `DOMAIN_STATUS.md` to record `PARTIAL / BLOCKED_BY_SAMPLE`.

## Validation

- `gh issue view` confirmed #130-#134/#136 are closed and #135/#96 are open.
- `ddd-migration-metrics.ps1` ran successfully and reported product proxy 30.8%.
- `ActivityProductReadModelQueryServiceTest` rerun passed 3/3.
- `check-harness-limits.ps1` passed before document edits.
- Read-only SQL confirmed product repair consistency: 18201 promoting snapshots, 18201 repaired states, 0 unrepaired.
- Read-only SQL confirmed the #135 blocker: 13 active mappings but 0 real-pre orders with `pick_source`.

## Harness Upgrade

No Harness behavior change required. This task only syncs issue state and closeout evidence.
