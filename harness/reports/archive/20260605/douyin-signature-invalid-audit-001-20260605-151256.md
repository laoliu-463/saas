# DOUYIN-SIGNATURE-INVALID-AUDIT-001

- Time: 2026-06-05 15:12:56 CST
- Env: local real-pre, remote real-pre sampled
- Scope: readonly audit, reports only
- Branch: feature/auth-system
- Local commit: cdc9031f
- Remote commit sampled: 72a5eeb
- Conclusion: PARTIAL, blocked by platform-side credential/status confirmation

## 1. Problem Restatement

Current chain status:

- Order field mapping: PASS, do not run ORDER-FIELD-MAPPING-FIX-001.
- 6468 estimated/paid field mapping: PASS.
- Fen/yuan conversion: PASS.
- Order table persisted facts: PASS.
- Settlement track sample: BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE.
- Upstream API runtime: FAIL, `isv.signature-invalid`.
- Order to performance coverage: PARTIAL, missing 12 `performance_records`.

This audit focuses only on `isv.signature-invalid` for Douyin upstream APIs.

## 2. Evidence Summary

### 2.1 Runtime Timeline

Local backend logs show the same container and same public runtime configuration moved from success to failure within a 10 minute window:

- 2026-06-05 11:30:00 CST: 2704 settlement sync succeeded; 6468 order sync succeeded with fetched=100, inserted=7, updated=93.
- 2026-06-05 11:40:00 CST: 2704 succeeded; 6468 succeeded with fetched=100, inserted=8, updated=92.
- 2026-06-05 11:50:00 CST: 2704 succeeded; 6468 succeeded with fetched=100, inserted=11, updated=89.
- 2026-06-05 12:30:43 CST: 2704 still succeeded, settlement failed=0.
- 2026-06-05 12:40:00 CST: 6468 and 2704 started returning `code=40003, subCode=isv.signature-invalid`.

Local log count since 2026-06-05 12:00 CST:

- `alliance.colonelActivityProduct`: 219 signature-invalid, first 2026-06-05 14:13:20 CST, last 2026-06-05 15:00:28 CST.
- `buyin.colonelMultiSettlementOrders`: 11 signature-invalid, first 2026-06-05 12:40:00 CST, last 2026-06-05 15:00:00 CST.
- `buyin.instituteOrderColonel`: 8 signature-invalid, first 2026-06-05 12:40:00 CST, last 2026-06-05 15:00:00 CST.

Remote real-pre also shows signature-invalid:

- `alliance.colonelActivityProduct`: 610, first 2026-06-05 12:35:00 CST, last 2026-06-05 15:05:30 CST.
- `buyin.colonelMultiSettlementOrders`: 20, first 2026-06-05 12:40:00 CST, last 2026-06-05 15:00:00 CST.
- `buyin.instituteOrderColonel`: 15, first 2026-06-05 12:40:00 CST, last 2026-06-05 15:00:00 CST.

Inference: this is not isolated to order fields or a single API. The failure affects multiple Douyin API methods that share the same signing client.

### 2.2 Source Path Evidence

- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:227` uses `Instant.now().toEpochMilli()` as timestamp.
- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:234` removes `appId` from payload before signing.
- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:236` calls `SignUtil.sign(appId, appSecret, methodName, timestamp, paramJson, "2")`.
- `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java:416` appends `access_token` to URL after signing.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:44` maps 6468 to `buyin.instituteOrderColonel`.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:45` maps 2704 to `buyin.colonelMultiSettlementOrders`.
- `backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java:90` and `:157` both call the same `douyinApiClient.post(...)`.
- `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java:95` and `:126` route settlement/order list through the real gateway.
- `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java:214` logs upstream mode and masked app key.

Inference: 6468 and 2704 share the same signing wrapper. Activity product sync also failing with the same subCode further supports a shared signing or platform credential problem.

### 2.3 SDK Contract Evidence

The bundled SDK dependency is `com.doudian:open-sdk:1.1.0` in `backend/pom.xml`.

Local `javap` inspection of `backend/lib/maven-repo/com/doudian/open-sdk/1.1.0/open-sdk-1.1.0.jar` shows:

- `DefaultDoudianOpClient` uses `System.currentTimeMillis()` for timestamp.
- URL includes `app_key`, `method`, `v=2`, `sign`, `timestamp`, `access_token`.
- SDK `SignUtil.sign(...)` signs app key, method, `param_json`, timestamp and version.

Inference: current millisecond timestamp behavior matches the bundled SDK. The "timestamp should be seconds" hypothesis is low confidence unless official platform documentation for these specific methods has changed.

### 2.4 Environment Evidence

Local real-pre backend container:

- `SPRING_PROFILES_ACTIVE=real-pre`
- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- masked app id/key are present and same shape
- client secret present, length 36
- `DOUYIN_APP_BASE_URL=ABSENT`, default openapi base URL path is used

Remote real-pre has equivalent public Douyin environment values. Secret values were compared without printing and evaluated equal for local vs remote.

Inference: there is no current evidence of local-vs-remote env drift. The likely configuration problem, if confirmed, is current deployed secret/key no longer matching platform-side app credential state.

### 2.5 Token Evidence

Local Redis token readiness:

- `douyin:token:*`: exists, TTL about 5.9 days.
- `douyin:refresh:*`: exists, TTL about 14 days.
- `douyin:token:expire_at:*`: exists.

Remote Redis token readiness is equivalent. Preflight also reports Douyin token readiness PASS with `reauthorizeRequired=false`.

Inference: token expiry or refresh failure is low confidence for this symptom. The error observed is `isv.signature-invalid`, not an access-token missing/expired error.

### 2.6 Clock Evidence

- Host local time and backend container UTC time are aligned locally.
- Remote host UTC and backend container UTC are aligned.

Inference: current evidence does not support clock drift as the primary cause.

### 2.7 Preflight Evidence

`npm run e2e:real-pre:p0:preflight` completed with exit code 0.

Output directory:

- `runtime/qa/out/real-pre-preflight-20260605-151043/report.md`
- `runtime/qa/out/real-pre-preflight-20260605-151043/summary.json`

Preflight status: PASS.

Checks passed:

- frontend real-pre 3001
- backend health 8081
- admin login
- real-pre env guard
- Douyin token readiness
- database schema readiness
- reusable promotion mapping
- QA cleanup plan available

Inference: baseline real-pre environment is runnable. The failure is after environment readiness, at upstream signature validation.

## 3. Cause Classification

### High Confidence

Platform-side credential/state mismatch:

- Same code and same public runtime config succeeded at 12:30 CST and failed at 12:40 CST.
- Local and remote both fail.
- Multiple API methods fail with the same signature-invalid subCode.
- Token readiness is PASS.
- Timestamp behavior matches SDK 1.1.0.

This still needs platform-side confirmation. The local audit cannot verify whether app secret was rotated, reset, disabled, or whether app credential state changed in the Douyin/Doudian platform console.

### Medium Confidence

Douyin platform signing validation or application authorization status change:

- Cross-method failure supports platform-side involvement.
- Historical 40003 samples in this repo include authorization subject mismatch, but those had different subCodes/messages and must not be treated as the same root cause.

Needs official platform console log, app audit/status record, or support response tied to the failing `log_id`.

### Low Confidence

Token problem:

- Redis access/refresh tokens exist with healthy TTL.
- Preflight token readiness PASS.
- Error subCode is signature invalid, not token expired/missing.

SDK/sign algorithm problem:

- Current implementation matches SDK 1.1.0 timestamp and sign parameter shape.
- No recent local diff touched `DouyinApiClient`, order gateway, or signing path between sampled local and remote commits.

Timestamp problem:

- Millisecond timestamp matches SDK.
- Host/container time aligned.

Environment variable pollution:

- local/remote public Douyin env values match.
- real-pre guards show live mode and test flags disabled.

Order field mapping:

- Activity product API also fails with signature-invalid.
- 6468/2704 mapping has already been separately marked PASS.

## 4. Stage Conclusion

This is not an order field mapping defect and should not trigger `ORDER-FIELD-MAPPING-FIX-001`.

The highest-confidence current explanation is:

> The deployed real-pre Douyin app key/secret configuration is internally consistent between local and remote, but likely no longer matches Douyin platform-side credential or application signing state after about 2026-06-05 12:40 CST.

This is a staged conclusion, not a final root cause. Final confirmation requires platform-side evidence: app credential history/status, console configuration, or official log inspection for the failing requests.

## 5. Recommended Actions

### Temporary Containment

Optional and requires user/operator confirmation:

- Pause or reduce order, settlement and activity product upstream polling while signature-invalid persists.
- This is only log/noise containment. It does not fix root cause and will delay new order/settlement samples.

### Root Cause Fix Path

1. In Douyin/Doudian platform console, verify the app corresponding to masked key `7623****7199`.
2. Check whether app secret was rotated, reset, disabled, regenerated, or whether app status changed between 2026-06-05 12:30 and 12:40 CST.
3. Check official/open-platform request logs using the failing upstream `log_id` values.
4. If a new secret is confirmed, update `DOUYIN_CLIENT_SECRET` through the controlled secret path for local and remote real-pre. Do not paste secrets into chat, reports, git, or logs.
5. Restart backend only after controlled env update.
6. Re-run real-pre preflight.
7. Run read-only probes for:
   - `buyin.instituteOrderColonel`
   - `buyin.colonelMultiSettlementOrders`
   - `alliance.colonelActivityProduct`
8. Only after upstream success, resume:
   - `ORDER-PERFORMANCE-MISSING-AUDIT-001`
   - `ORDER-SETTLEMENT-SAMPLE-VERIFY-001`

### Long-Term Governance

- Add a Douyin credential rotation runbook.
- Add a non-secret credential drift checklist comparing masked app key, secret presence/length/hash fingerprint, token owner and platform app status.
- Add sanitized signing trace around failures: method, app key mask, timestamp length, parameter JSON hash, response code/subCode/log_id. Do not log token, sign or secret.
- Add a contract test that compares project request shape with bundled SDK behavior without using real credentials.

## 6. Verification Checklist After Credential Recovery

- `npm run e2e:real-pre:p0:preflight` PASS.
- backend health `/api/system/health` UP.
- 6468 read-only order query returns success or a non-signature business error.
- 2704 read-only settlement query returns success or a non-signature business error.
- activity product sync probe returns success or a non-signature business error.
- backend logs show no new `isv.signature-invalid` for the same app/methods.
- Redis token readiness remains PASS.
- No `.env*`, secret, token, sign or OAuth code is printed or committed.

## 7. Explicit Non-Actions

- Did not modify order field mapping.
- Did not backfill settlement fields from estimated fields.
- Did not treat zero settlement as a code bug.
- Did not change dashboard formulas.
- Did not clear database.
- Did not perform remote deployment.
- Did not expose secrets.
