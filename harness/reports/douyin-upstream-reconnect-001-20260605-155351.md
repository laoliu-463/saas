# DOUYIN-UPSTREAM-RECONNECT-001

- Time: 2026-06-05 15:53:51 CST
- Env: local real-pre
- Scope: upstream reconnection verification
- Branch: feature/auth-system
- Commit before report: 220f31ea
- Conclusion: PASS_UPSTREAM_RECONNECTED

## 1. Problem Restatement

User confirmed the root cause of `isv.signature-invalid` was app secret reset.

This task verifies that the new local `real-pre` secret is loaded into the backend runtime and that the Douyin upstream APIs recover without changing order field mapping or dashboard formulas.

## 2. Actions

1. Ran real-pre safety check.
2. Compared `.env.real-pre` and current backend container environment without printing secret values.
3. Confirmed `DOUYIN_CLIENT_SECRET` differed before restart.
4. Restarted local `backend-real-pre` through the Harness compose script.
5. Verified backend health and Docker health.
6. Confirmed `DOUYIN_CLIENT_SECRET` in `.env.real-pre` now equals the backend container environment value, without printing the secret.
7. Ran real-pre preflight.
8. Ran readonly upstream probes for:
   - `alliance.colonelActivityProduct`
   - `buyin.colonelMultiSettlementOrders`
   - `buyin.instituteOrderColonel`
9. Waited for one scheduler window and checked backend logs.
10. Queried database facts to confirm recent order/performance write effects from resumed sync.

## 3. Evidence

### 3.1 Safety and Runtime

Safety check:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope full -DryRun
```

Result: PASS.

Runtime guard facts:

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- `DOUYIN_CLIENT_SECRET`: present, not printed

Before restart:

- `.env.real-pre` and running backend container had different `DOUYIN_CLIENT_SECRET` values.
- This matched the confirmed secret reset scenario: file was updated, container had not loaded it yet.

Restart command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope backend
```

Result:

- backend Docker image rebuilt from existing jar.
- `backend-real-pre` recreated.
- Redis container was also recreated by Compose dependency handling.
- No volume delete and no `docker compose down -v`.

After restart:

- backend Docker health: healthy.
- Redis/PostgreSQL/frontend: healthy.
- backend `/api/system/health`: `{"status":"UP"}`.
- `.env.real-pre` and backend container `DOUYIN_CLIENT_SECRET` are equal by non-secret comparison.

### 3.2 Preflight

Command:

```powershell
npm run e2e:real-pre:p0:preflight
```

Evidence directory:

```text
runtime/qa/out/real-pre-preflight-20260605-154631
```

Result: PASS.

Passed checks:

- frontend real-pre 3001
- backend health 8081
- admin login
- real-pre env guard
- Douyin token readiness
- database schema readiness
- reusable promotion mapping
- QA cleanup plan available

### 3.3 Upstream Probe Results

Token status:

- hasAccessToken: true
- hasRefreshToken: true
- reauthorizeRequired: false

Activity product probe:

- endpoint: `alliance.colonelActivityProduct`
- localStatus: success
- remote summary: `test=false`, `activityId=3920684`, `items=array(len=1)`

Settlement order probe:

- endpoint: `buyin.colonelMultiSettlementOrders`
- localStatus: success
- remoteCode: `10000`
- remoteMessage: `success`
- remoteLogId: `20260605154825AFDD5629C639EC871280`
- rowHint: 0

Institute order raw probe:

- endpoint: `buyin.instituteOrderColonel`
- localStatus: success
- remoteCode: `10000`
- remoteMessage: `success`
- remoteLogId: `202606051548254904E44EB385F1BE1153`
- rowHint: 1

### 3.4 Scheduler Evidence

Backend logs after restart show no new `isv.signature-invalid`.

Count since 2026-06-05 15:45 CST:

- `isv.signature-invalid` / `signature-invalid` / `code=40003`: 0

Scheduler window:

- 2026-06-05 15:50:01 CST: `buyin.colonelMultiSettlementOrders` success.
- 2026-06-05 15:50:01 CST: `ORDER_SYNC_SETTLEMENT ... pages=0 fetched=0 inserted=0 updated=0 failed=0`.
- 2026-06-05 15:50:01 CST: `buyin.instituteOrderColonel` success.
- 2026-06-05 15:50:07 CST: `ORDER_SYNC_INSTITUTE ... pages=1 fetched=100 inserted=89 updated=11 attributed=0 unattributed=100 failed=0`.
- Multiple `alliance.colonelActivityProduct` calls succeeded after 15:50 CST.

### 3.5 Database Facts

Readonly SQL evidence after restart:

- `colonelsettlement_order` total: 1221.
- Orders updated in last 20 minutes: 100.
- Orders created in last 20 minutes: 251.
- Latest order `update_time`: 2026-06-05 07:50:03 UTC.
- `performance_records` total: 1163.
- `performance_records` created in last 20 minutes: 43.
- Orders without `performance_records`: 58.

Interpretation:

- Upstream sync is writing order facts again.
- Performance coverage is still not complete and must continue under `ORDER-PERFORMANCE-MISSING-AUDIT-001`.

## 4. Conclusion

The upstream signature failure is recovered in local real-pre after loading the reset secret into the backend runtime.

Confirmed recovered methods:

- `alliance.colonelActivityProduct`
- `buyin.colonelMultiSettlementOrders`
- `buyin.instituteOrderColonel`

This task does not close the downstream performance coverage problem. Current database evidence shows 58 orders without `performance_records`, so `ORDER-PERFORMANCE-MISSING-AUDIT-001` remains the next required task.

## 5. Explicit Non-Actions

- Did not modify order field mapping.
- Did not backfill settlement fields from estimated fields.
- Did not change dashboard formulas.
- Did not clear database.
- Did not delete PostgreSQL or Redis volumes.
- Did not print, commit, or expose the new secret.
- Did not deploy remote real-pre.

## 6. Next Steps

1. Run `ORDER-PERFORMANCE-MISSING-AUDIT-001` against the current post-recovery dataset.
2. After performance coverage is understood or repaired, run `ORDER-SETTLEMENT-SAMPLE-VERIFY-001`.
3. If remote real-pre must also recover, update `/opt/saas/env/.env.real-pre` through the controlled secret path, restart remote backend, and repeat this verification. This report only covers local real-pre.
