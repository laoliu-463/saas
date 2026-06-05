# Evidence Report - DOUYIN-SIGNATURE-INVALID-AUDIT-001

- Time: 2026-06-05 15:12:56 CST
- Env: real-pre
- Scope: readonly audit / reports only
- Branch: feature/auth-system
- Commit hash: cdc9031f
- Worktree clean: no
- Remote deployed: no
- Remote sampled: yes, remote commit 72a5eeb
- Conclusion: PARTIAL

## Worktree State

Pre-existing modified/untracked files were present before this report was created:

- `frontend/src/views/data/index.test.ts`
- `frontend/src/views/data/index.vue`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/evidence-20260605-102656-dashboard-full-money-recon-001.md`
- `harness/reports/evidence-20260605-142932-order-field-mapping-audit-001.md`
- `harness/reports/order-field-mapping-audit-001-20260605-142932.md`

This audit only adds DOUYIN-SIGNATURE-INVALID-AUDIT-001 report files.

## Safety Check

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

Result: PASS.

Notes:

- Secret presence was checked without exposing secret values.
- Real-pre forbidden flags were not enabled.

## Build Result

Skipped.

Reason: readonly audit / reports-only scope. No backend or frontend source code was modified.

## Docker Status

Local sampled status:

- `saas-active-frontend-real-pre-1`: Up healthy, port 3001.
- `saas-active-backend-real-pre-1`: Up healthy, port 8081.
- PostgreSQL real-pre: healthy.
- Redis real-pre: healthy.

Remote sampled status:

- real-pre containers healthy.

## Health Check Result

Local sampled checks:

- backend `/api/system/health`: `{"status":"UP"}`.
- frontend `/healthz`: HTTP 200.

Preflight checks also passed backend health and frontend login page readiness.

## Business Validation Result

Command:

```powershell
npm run e2e:real-pre:p0:preflight
```

Exit code: 0.

Evidence directory:

```text
runtime/qa/out/real-pre-preflight-20260605-151043
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

## Upstream Runtime Evidence

Local logs since 2026-06-05 12:00 CST:

- signature-invalid total: 238.
- `buyin.instituteOrderColonel`: 8.
- `buyin.colonelMultiSettlementOrders`: 11.
- `alliance.colonelActivityProduct`: 219.

Remote logs since 2026-06-05 12:00 CST:

- signature-invalid total: 645.
- `buyin.instituteOrderColonel`: 15.
- `buyin.colonelMultiSettlementOrders`: 20.
- `alliance.colonelActivityProduct`: 610.

Critical local transition:

- 2026-06-05 12:30 CST: 2704 still succeeded.
- 2026-06-05 12:40 CST: 6468/2704 started failing with `isv.signature-invalid`.

## Environment Evidence

Local and remote public Douyin env values were equivalent:

- `SPRING_PROFILES_ACTIVE=real-pre`
- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- masked app id/key match
- client secret present
- `DOUYIN_APP_BASE_URL=ABSENT`

Secret values were not printed.

## Token Evidence

Local and remote Redis token keys exist with healthy TTL:

- access token key exists
- refresh token key exists
- expire_at key exists

Preflight token readiness: PASS, `reauthorizeRequired=false`.

## Source Evidence

- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:227` uses millisecond timestamp.
- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:236` signs app id, secret, method, timestamp, param JSON and v=2.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:44` defines 6468 method.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:45` defines 2704 method.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:90` and `:157` call the shared client.
- bundled `com.doudian:open-sdk:1.1.0` also uses `System.currentTimeMillis()` for timestamp.

## Remote Deployment

Not executed.

Reason: user requested readonly audit. Remote real-pre was sampled only for comparison.

## Final Evidence Conclusion

PARTIAL.

Evidence is sufficient to deprioritize order field mapping, token refresh failure, timestamp unit mismatch, local-vs-remote env drift and recent signing-code change as primary causes.

Evidence is not sufficient to declare final root cause because platform-side app credential/status history cannot be verified from this workspace. The next required evidence is Douyin/Doudian platform console or official log confirmation for app credential/signature validation state around 2026-06-05 12:30-12:40 CST.

## Remaining Risks

- Platform-side app secret rotation/reset/status change has not been externally confirmed.
- Failing upstream `log_id` values were observed in logs but not checked against official platform tooling.
- Until signature-invalid is resolved, settlement sample verification remains blocked.
- Until upstream is recovered, order replenishment and refund/settlement validation can produce misleading negatives.
