# Evidence Report - DOUYIN-UPSTREAM-RECONNECT-001

- Time: 2026-06-05 15:53:51 CST
- Env: local real-pre
- Scope: upstream reconnection verification
- Branch: feature/auth-system
- Commit before report: 220f31ea
- Worktree clean: no, pre-existing dirty files remain
- Remote deployed: no
- Conclusion: PASS_UPSTREAM_RECONNECTED

## Worktree State

Pre-existing dirty files were present before this task and were not staged or modified by this recovery work:

- `frontend/src/views/data/index.test.ts`
- `frontend/src/views/data/index.vue`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md`
- `harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md`
- `harness/reports/order-field-mapping-audit-001-20260605-142932.md`

This task adds only the reconnect report, evidence report and retro summary.

## Safety Check

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope full -DryRun
```

Result: PASS.

Secret values were not printed.

## Config Runtime Check

Before restart:

- `DOUYIN_CLIENT_SECRET` was present in `.env.real-pre`.
- `DOUYIN_CLIENT_SECRET` was present in backend container env.
- Non-secret equality check: false.

After restart:

- `DOUYIN_CLIENT_SECRET` was present in `.env.real-pre`.
- `DOUYIN_CLIENT_SECRET` was present in backend container env.
- Length: 36 in both places.
- Non-secret equality check: true.

## Restart

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope backend
```

Result: PASS.

Notes:

- Docker Compose recreated `backend-real-pre`.
- Docker Compose also recreated Redis due dependency handling.
- No `down -v`.
- No PostgreSQL or Redis volume deletion.

## Health Check

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope backend
```

Result: PASS.

Backend health:

```json
{"status":"UP"}
```

Docker status:

- `saas-active-backend-real-pre-1`: healthy
- `saas-active-redis-real-pre-1`: healthy
- `saas-active-frontend-real-pre-1`: healthy
- `saas-active-postgres-real-pre-1`: healthy

## Preflight

Command:

```powershell
npm run e2e:real-pre:p0:preflight
```

Exit code: 0.

Evidence directory:

```text
runtime/qa/out/real-pre-preflight-20260605-154631
```

Result: PASS.

## Upstream Probes

Readonly/manual probe summary:

| Probe | Endpoint | Result | Remote code | Message | Row hint |
| --- | --- | --- | --- | --- | --- |
| Token status | `douyin-token-status` | success | - | `hasAccessToken=True, hasRefreshToken=True, reauthorizeRequired=False` | - |
| Activity product list | `alliance.colonelActivityProduct` | success | - | `test=false`, `items=array(len=1)` | 1 item |
| Settlement order | `buyin.colonelMultiSettlementOrders` | success | `10000` | `success` | 0 |
| Institute order raw | `buyin.instituteOrderColonel` | success | `10000` | `success` | 1 |

Relevant upstream log IDs:

- 2704: `20260605154825AFDD5629C639EC871280`
- 6468: `202606051548254904E44EB385F1BE1153`

## Scheduler Evidence

Backend logs since 2026-06-05 15:45 CST:

- signature-invalid count: 0.
- `buyin.colonelMultiSettlementOrders`: success.
- `buyin.instituteOrderColonel`: success.
- `alliance.colonelActivityProduct`: success.

Scheduled order sync:

- Settlement sync: `pages=0 fetched=0 inserted=0 updated=0 failed=0`.
- Institute sync: `pages=1 fetched=100 inserted=89 updated=11 failed=0`.

## Database Evidence

Readonly SQL after recovery:

| Metric | Value |
| --- | --- |
| Orders total | 1221 |
| Orders updated in last 20 minutes | 100 |
| Orders created in last 20 minutes | 251 |
| Latest order update time | 2026-06-05 07:50:03 UTC |
| Performance records total | 1163 |
| Performance records created in last 20 minutes | 43 |
| Orders without performance records | 58 |

## Build

Source build skipped.

Reason: no source code changes. Docker image rebuild/recreate was executed by `restart-compose.ps1` using existing backend jar.

## Remote

Remote real-pre was not updated or deployed in this task.

## Final Evidence Conclusion

PASS_UPSTREAM_RECONNECTED for local real-pre upstream signing/runtime.

Residual downstream status:

- `ORDER-PERFORMANCE-MISSING-AUDIT-001`: still required.
- `ORDER-SETTLEMENT-SAMPLE-VERIFY-001`: can resume after performance coverage audit, but still depends on real non-zero settlement samples.

No secret, token, password, signature, OAuth code, or private credential was printed or committed.
