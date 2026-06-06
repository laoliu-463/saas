# Evidence Report - DOUYIN-UPSTREAM-RECONNECT-LOCAL-001

## Metadata

- Time: 2026-06-06 12:51:34 +08:00
- Environment: local real-pre
- Scope: readonly verification / no code change
- Branch: feature/auth-system
- Commit: 70d1e400
- Remote deploy: not executed per user instruction
- Conclusion: PARTIAL

## Task Boundary

User changed scope from remote real-pre to local real-pre only:

```text
服务器远端先不部署，只做本地real-pre验证
```

This run did not deploy remote, did not modify Java/Vue/SQL/Docker/env, did not clear data, and did not restart containers.

## Git Intake

Pre-existing untracked report files were present before this verification:

```text
harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md
harness/reports/evidence-20260606-121756-order-performance-backfill-001.md
harness/reports/evidence-20260606-122912.md
harness/reports/order-field-mapping-audit-001-20260605-142932.md
harness/reports/retro-20260606-122926.md
```

They were not staged or committed in this task.

## Safety And Runtime Health

### Safety check

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope full -DryRun
```

Result: PASS.

Real-pre guard values sampled without exposing secrets:

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- `DOUYIN_CLIENT_SECRET`: present

### Docker status

Command:

```powershell
docker compose --env-file .\.env.real-pre -f .\docker-compose.real-pre.yml ps
```

Result: four local real-pre containers healthy:

- `backend-real-pre`: healthy, port 8081
- `frontend-real-pre`: healthy, port 3001
- `postgres-real-pre`: healthy
- `redis-real-pre`: healthy

### Health checks

- Backend `/api/system/health`: `{"status":"UP"}`
- Frontend `/healthz`: `ok`

### Backend container env

Sampled public runtime flags:

```text
SPRING_PROFILES_ACTIVE=real-pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
ORDER_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_ENABLED=true
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true
ALLOW_REAL_PROMOTION_WRITE=true
```

## Upstream Reconnect Evidence

Backend logs in the latest 30 minutes show no new `isv.signature-invalid` hits and show successful calls for the three relevant upstream paths:

- `alliance.colonelActivityProduct`: repeated success; `ProductActivitySyncJob finished, ok=3, fail=0` observed.
- `buyin.colonelMultiSettlementOrders`: success at 2026-06-06 12:40 CST, `pages=0 fetched=0 failed=0`.
- `buyin.instituteOrderColonel`: success at 2026-06-06 12:40 CST, `pages=1 fetched=100 inserted=5 updated=95 failed=0`.

Observed order sync log:

```text
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT ... pages=1 fetched=100 inserted=5 updated=95 attributed=0 unattributed=100 noPickSource=0 noMapping=100 failed=0
```

Stage conclusion: local real-pre Douyin upstream signature/connectivity has recovered enough for product and order API calls. This does not prove remote recovery and does not prove business attribution closure.

## Preflight

Command:

```powershell
npm run e2e:real-pre:p0:preflight
```

Result: PASS.

Evidence:

```text
runtime/qa/out/real-pre-preflight-20260606-124246/report.md
runtime/qa/out/real-pre-preflight-20260606-124246/summary.json
```

Preflight checks passed:

- frontend real-pre 3001
- backend health 8081
- admin login
- real-pre env guard
- Douyin token readiness
- database schema readiness
- reusable promotion mapping
- QA cleanup plan available

## SQL Reconcile

All SQL queries were read-only. Initial queries using old assumptions failed because current schema uses text `pick_source` and `performance_records` has no `deleted` column; corrected queries were rerun against actual schema.

### Orders

Query result:

```text
orders_total=1381
pick_source_nonzero=0
channel_nonzero=0
no_mapping=0
settle_nonzero=0
max_pay_time=2026-06-06 12:38:49
```

Latest five orders all have empty `pick_source`, `UNATTRIBUTED`, nonzero `order_amount`, and empty `settle_amount`.

### Performance records

Query result:

```text
perf_total=1381
perf_settle_nonzero=0
perf_distinct_orders=1381
missing_performance=0
```

This confirms the previous order-to-performance missing-record gap is currently closed locally, but settlement track remains blocked by lack of real settlement sample.

### Mapping table

Query result:

```text
mappings_total=13
mappings_pick_source_nonzero=13
latest_mapping=2026-06-01 12:09:09.21191
```

Reusable mappings exist, but current real orders do not carry matching `pick_source`.

## P0 E2E

Command:

```powershell
npm run e2e:real-pre:p0
```

Result: FAIL.

Evidence:

```text
runtime/qa/out/real-pre-p0-20260606-124354/report.md
runtime/qa/out/real-pre-p0-20260606-124354/summary.json
```

Step results:

| Step | Result | Evidence |
| --- | --- | --- |
| preflight | PASS | P0 step 01 |
| 08 Douyin integration | FAIL | UI waited for `活动商品已刷新` and timed out |
| 31 product chain | PASS | activity/product upstream probes succeeded |
| 32 order attribution | PENDING | no upstream orders in the 30-minute script window |
| 33 sample chain | FAIL | biz audit returned HTTP 403 |
| 34 performance dashboard | PENDING | no readable performance sample for formula check |
| 35 RBAC | FAIL | `channel_leader GET /api/samples/exports?page=1&size=1` returned HTTP 200 |
| 36 cleanup plan | PASS_NEEDS_CLEANUP | plan-only cleanup safe |

## Current Evidence Chain

Phenomenon:

- Previous `isv.signature-invalid` issue blocked upstream calls.
- Current local real-pre upstream calls are succeeding again.
- Full local real-pre P0 still fails.

Evidence:

- Safety check PASS.
- Containers healthy.
- Health checks PASS.
- Preflight PASS.
- Backend logs show successful product/order upstream calls and no recent signature-invalid.
- SQL shows 1381 orders, 1381 performance records, zero missing performance records.
- SQL also shows zero orders with non-empty `pick_source`, zero channel attribution, zero nonzero settlement amount.
- P0 E2E final status FAIL with three FAIL steps and two PENDING steps.

Inference:

- The local signature-invalid/connectivity problem is currently recovered.
- Channel attribution remains blocked by real sample data: existing orders do not carry `pick_source`.
- Settlement verification remains blocked by real settlement sample: `settle_amount` is still empty/zero.
- P0 has independent system/test failures in Douyin UI state assertion, sample audit permission/assignment path, and RBAC export permission.

Stage conclusion:

- Local upstream reconnect: PASS on current evidence.
- Local real-pre full P0: FAIL.
- Overall task status: PARTIAL.

## Not Done / Blockers

- Remote real-pre was not deployed or sampled in this turn by user instruction.
- Full real-pre P0 is not passing.
- Channel attribution cannot be marked PASS without a real system-generated `pick_source` order.
- Settlement track cannot be marked PASS without a real settlement sample.
- 08/33/35 P0 failures need separate diagnosis before claiming real-pre readiness.
