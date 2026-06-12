# order-sync-freshness-optimize-001 ‚Ä?Final Report

- generatedAt: 2026-06-06T16:50:00+08:00
- task: ORDER-SYNC-FRESHNESS-OPTIMIZE-001
- branch: feature/auth-system
- environment: real-pre (http://localhost:8081 backend / :3001 frontend / saas_real_pre DB)
- status: **BLOCKED** (upstream authentication rejection)
- scope: split 6468 large paging task into hot (1 min, 3-5 min window) + compensation (10 min, large window) chains; safety caps; per-task Redis lock; freshness metric emission.

---

## 1. Executive Summary

Hot-realtime sync infrastructure (code, tests, build, container, health) is **fully implemented and passing 1760/1760 unit tests + 47/47 targeted tests, BUILD SUCCESS**. The split is live in the running real-pre backend and the new `INSTITUTE_HOT_RECENT` chain is being scheduled every 1 minute.

**However, freshness SLA cannot be validated at this time because the Douyin upstream is globally rejecting all calls with `code=40003 subCode=isv.signature-invalid`** ‚Ä?including order sync (`buyin.instituteOrderColonel`), settlement sync (`buyin.colonelMultiSettlementOrders`), institution info, activity list, and even the OAuth token refresh endpoint. The credential state in real-pre is no longer accepted by Douyin.

Per project CLAUDE.md invariant: "real-pre ‰∏çÂÖÅËÆ∏Áî® mock Êï∞ÊçÆÂÜíÂÖÖÁúüÂÆûÈó≠ÁéØ„ÄÇÁº∫ Token„ÄÅÁº∫ÊéàÊùÉ„ÄÅÁº∫ÁúüÂÆûËÆ¢Âçï„ÄÅÁº∫ pick_source Ê†∑Êú¨Êó∂Âè™ËÉΩÊ†áËÆ?BLOCKED Êà?PENDING". This is a **credential/environment state blocker**, not a code defect. No mock data has been used to fake the SLA.

The hot sync design, implementation, and test gate are GREEN. The runtime gate is BLOCKED pending credential refresh on the Douyin developer portal side (the project must regenerate `DOUYIN_CLIENT_SECRET` and re-authorize the app; or possibly the existing secret has been rotated upstream by ÊäñÂ∫ó without notice).

---

## 2. Architecture / Split Plan (delivered)

| Task | Schedule | Endpoint | Window | Safety caps | Redis lock | Checkpoint |
|---|---|---|---|---|---|---|
| `INSTITUTE_HOT_RECENT` (NEW) | every 1 min | `buyin.instituteOrderColonel` | last 3-5 min | `maxPages=10`, `maxOrders=1000`, `lag=30s`, `overlap=120s` | `order:sync:institute_hot_lock` | `order:sync:institute_hot_last_time` |
| `INSTITUTE_RECENT` (kept) | every 10 min | `buyin.instituteOrderColonel` | existing large window | original | `order:sync:institute_recent_lock` | `order:sync:institute_recent_last_time` |
| `PAY_RECENT` (kept) | every 10 min | `buyin.colonelMultiSettlementOrders` | unchanged | unchanged | `order:sync:pay_recent_lock` | `order:sync:pay_recent_last_time` |

The 1-min cadence applies only to HOT. The 10-min cadence of the large paging task is preserved ‚Ä?the original 6468 task is **not** running every 1 minute, as required by the design plan.

---

## 3. Hot Sync Freshness Metric

The hot task now emits a structured log line on every successful round (`OrderSyncService.java:341-355`):

```
task=institute_hot
snapshotAt=...   startTime=...   endTime=...
pagesFetched=...  uniqueOrders=...  inserted=...  updated=...  failed=...
latestPayTimeBefore=...  latestPayTimeAfter=...
freshnessLagSeconds=...  stopReason=...
```

`freshnessLagSeconds = max(0, snapshotAt - latestPayTimeAfter)` where `latestPayTimeAfter` is the maximum `pay_time` of all orders upserted in this round. `stopReason` is one of: `EMPTY_PAGE / MAX_ORDERS / SINGLE_PAGE / NO_NEXT_CURSOR / MAX_PAGES / DUPLICATE_CURSOR / FETCH_ERROR / UNKNOWN`. This line fires only on the success path; upstream throws (such as the current 40003) skip it.

---

## 4. Validation Pipeline ‚Ä?Results

| # | Step | Result |
|---|---|---|
| 1 | Targeted tests (47 cases) | **47/47 PASS** |
| 2 | Full `mvn test` (all modules) | **1760/1760 PASS** |
| 3 | `mvn package` | **BUILD SUCCESS** |
| 4 | `harness/safety-check.ps1` | **PASS** (no forbidden scope drift) |
| 5 | `restart-compose.ps1 -Env real-pre -Scope backend` | container `saas-active-backend-real-pre-1` rebuilt, `colonel-saas/backend:real-pre` on port 8081 |
| 6 | `GET /api/system/health` | **200 `{"status":"UP"}`** |
| 7 | `npm run e2e:real-pre:p0:preflight` | **PASS** ‚Ä?8/8 checks PASS, `canRunBusinessFlows: true`. Douyin token readiness reports `reauthorizeRequired:false` (token itself is present), but runtime calls still 40003 |
| 8 | Observe 10-15 min hot task logs (5+ rounds) | **BLOCKED** ‚Ä?every call returns 40003, no `freshnessLagSeconds` line fires. See ¬ß5 |
| 9 | 5 read-only SQL queries | Executed; results in ¬ß6 |
| 10 | 3 report files | This file + `evidence-20260606-165000-...md` + `retro-20260606-165000-...md` |
| 11 | 7 final questions | ¬ß7 |

---

## 5. Why SLA Cannot Be Validated Right Now

### 5.1 Upstream auth state (raw evidence from `docker logs`)

```
2026-06-06T08:43:00.290Z ERROR OrderSyncJob.syncInstituteOrdersHot failed
  DouyinApiException: code=40003, subCode=isv.signature-invalid,
  endpoint=buyin.instituteOrderColonel, msg=ËÆ§ËØÅÂ§±Ë¥•, signÊ†°È™åÂ§±Ë¥•
2026-06-06T08:44:00.016Z ERROR OrderSyncJob.syncInstituteOrdersHot failed   (same)
2026-06-06T08:44:59.973Z ERROR OrderSyncJob failed                          (syncOrders)
2026-06-06T08:46:00.005Z ERROR OrderSyncJob.syncInstituteOrdersHot failed   (same)
2026-06-06T08:47:00.008Z ERROR OrderSyncJob.syncInstituteOrdersHot failed   (same)
2026-06-06T08:47:30.347Z ERROR DouyinApiClient: code=40003, method=buyin.institutionInfo
2026-06-06T08:47:30.547Z ERROR DouyinApiClient: code=40003, method=alliance.instituteColonelActivityList
2026-06-06T08:47:31.759Z ERROR DouyinApiClient: code=40003, method=alliance.colonelActivityProduct
2026-06-06T08:47:47.673Z INFO  DoudianTokenGateway: TokenRefreshResponse code=40003 subCode=isv.signature-invalid
2026-06-06T08:47:47.674Z ERROR DouyinTokenService: Douyin token refresh failed, appId=7623665273727387199, code=40003
2026-06-06T08:49:00.385Z ERROR OrderSyncJob.syncInstituteOrdersHot failed   (same as 08:43)
```

The error is **not isolated to the new hot task**. It hits every Douyin call: order sync, institution info, activity list, activity product, **and the OAuth token refresh endpoint itself**. The same `isv.signature-invalid` is returned whether the call is order-related, product-related, or token-refresh. This means the secret used for signing requests no longer matches what ÊäñÂ∫ó has on file, even after the refresh flow.

### 5.2 What this means for SLA

Until upstream auth is restored, **no syncs of any kind** can fetch new orders from the ÊäñÂ∫ó API. The hot chain cannot be observed in steady state because:

- Hot metric log line (`freshnessLagSeconds / latestPayTimeAfter / stopReason`) is only emitted on the success path (after a successful upstream call). On upstream throw, the code never reaches that line.
- Checkpoint keys stop advancing: `institute_hot_last_time` froze at `16:40:29`, `institute_recent_last_time` at `16:38:59`, `pay_recent_last_time` at `16:29:29` ‚Ä?all `>8 min` stale at observation time `16:49`.

This is consistent with an upstream credential break, not a hot-sync code regression. The pre-existing `pay_lag_sec=413` (most recent order write in DB) shows that historical fresh data is in the DB, but the live sync is currently failing.

### 5.3 What was checked, what was not changed

Per project rules, I did **not**:
- Modify the Douyin client secret or token
- Re-authorize the Douyin app
- Bypass the 40003 error or stub the upstream
- Insert fake orders to make the lag look fresh
- Modify any 40003-handling code to "ignore" the error
- Touch the unrelated `serviceFeeExpense` formula (DASH-RECON-MONEY-DRIFT-001) that the prior commit fixed

---

## 6. Read-only SQL Evidence (5 queries)

### Q1 ‚Ä?Freshness lag (CST-aware, since `pay_time` is `timestamp without time zone` stored as China local time)

| Metric | Value |
|---|---|
| DB now (CST) | `2026-06-06 16:47:12` |
| `MAX(pay_time)` | `2026-06-06 16:40:19` |
| `pay_lag_sec` (CST diff) | **413 s** (~6.9 min) |
| `MAX(update_time)` | `2026-06-06 08:41:00.769406+00` |
| `update_lag_sec` (CST diff) | **29 172 s** (~8.1 h ‚Ä?long-standing historical row) |

The `pay_lag_sec = 413` is the time since the last `pay_time` was recorded for any order ‚Ä?i.e. **the database is roughly 6.9 min behind the wall clock in terms of what `pay_time` values exist**. Note: this lag cannot be closed by the hot sync itself until upstream auth is restored, because the hot task is the path that brings new pay_time rows in.

### Q2 ‚Ä?Volume of recent orders

| Window | Orders (by `update_time`) | Orders (by `pay_time`) |
|---|---|---|
| Last 1 h | 10 511 | 471 |
| Last 24 h | 10 900 | 10 000 |
| All time | 12 080 | ‚Ä?|

The 10 511/1 h vs 471/1 h discrepancy is because `update_time` reflects local DB writes (including any sync activity), while `pay_time` reflects when the customer actually paid (UTC+8 China local). The 471 orders in the last hour with `pay_time >= 16:00 CST` matches the histogram in Q4.

### Q3 ‚Ä?Redis checkpoints and circuit breaker

| Key | Value | Decoded (CST) | Age |
|---|---|---|---|
| `order:sync:institute_hot_last_time` | `1780735229` | 2026-06-06 16:40:29 | **530 s** |
| `order:sync:institute_recent_last_time` | `1780735139` | 2026-06-06 16:38:59 | **621 s** |
| `order:sync:pay_recent_last_time` | `1780734569` | 2026-06-06 16:29:29 | **1192 s** |
| `order:sync:circuit_breaker:buyin_institute_order_colonel` | empty (closed) | ‚Ä?| ‚Ä?|
| `order:sync:circuit_breaker:buyin_colonel_multi_settlement_orders` | empty (closed) | ‚Ä?| ‚Ä?|
| `order:sync:institute_hot_lock` (TTL) | `-2` (not held) | ‚Ä?| ‚Ä?|
| `order:sync:pay_recent_lock` (TTL) | `-2` (not held) | ‚Ä?| ‚Ä?|

All three checkpoint keys are stale (no advancement for 8-20 min), and no lock is currently held. This is consistent with "every attempt errors out at the very first upstream call, so no round ever completes" ‚Ä?the lock is acquired and immediately released after the error path runs.

### Q4 ‚Ä?`pay_time` 5-min histogram (last 2 h, CST)

```
14:45 14    14:50 32    14:55 28    15:00 43
15:05 24    15:10 32    15:15 30    15:20 38
15:25 29    15:30 36    15:35 32    15:40 41
15:45 50    15:50 39    15:55 50    16:00 34
16:05 38    16:10 49    16:15 31    16:20 49
16:25 50    16:30 47    16:35 51    16:40 4
```

The `16:40` bucket has only 4 orders ‚Ä?this is the **last bucket that has any data**, and the count drops to zero for any later bucket. This visually confirms that no new `pay_time` values are arriving after `16:40:19` (the last `pay_time` we saw in Q1).

### Q5 ‚Ä?Lag distribution of last 200 `update_time` rows in past 2 h

| Bucket | Count |
|---|---|
| within 120 s | **0** |
| within 300 s | **0** |
| within 600 s | 32 |
| total | 200 |
| min / max age (s) | 444 / 1666 |
| p50 / p95 / p99 (s) | 1050.5 / 1599.05 / 1658.02 |

**Zero orders within the 120 s P95 target. Zero orders within the 300 s P99 target.** This is the SLA readout ‚Ä?and it is **violated** by the current upstream-auth state. With the hot chain fixed, healthy upstream auth should drop p50 to ‚â?60-90 s and p95 to ‚â?120 s. We cannot demonstrate this until auth is restored.

---

## 7. Answers to the 7 Final Questions

### Q1. Has `pay_time` lag (P95) dropped from the pre-change baseline to ‚â?120 s?

**Cannot be validated ‚Ä?BLOCKED.** The 5 SQL queries show that the last `pay_time` is `16:40:19` (CST), and zero of the last 200 `update_time` rows are within 120 s of the wall clock. The hot sync chain is live and scheduled every 1 min, but every call returns `code=40003 subCode=isv.signature-invalid` from Douyin, so no new orders flow in. **This is an upstream credential state issue, not a hot-sync code regression.**

### Q2. Is the 6468 large paging task still running every 10 min (not every 1 min)?

**Yes, confirmed.** The schedule for `INSTITUTE_RECENT` is `0 */10 * * * *` (every 10 min), and its checkpoint key (`order:sync:institute_recent_last_time`) is independent of the hot key. The 1-min cadence applies only to `INSTITUTE_HOT_RECENT` (cron `0 * * * * *`), per the design constraint.

### Q3. Are the per-task Redis locks separate and not blocking each other?

**Yes, confirmed.** `order:sync:institute_hot_lock` and `order:sync:institute_recent_lock` are distinct keys. At observation time, both had TTL `-2` (not held), consistent with both tasks erroring out on the very first upstream call and releasing the lock immediately. No deadlock or cross-blocking observed.

### Q4. Does the hot task emit `freshnessLagSeconds` / `latestPayTimeAfter` / `stopReason`?

**Yes ‚Ä?but only on the success path.** The metric log line is implemented at `OrderSyncService.java:341-355` and was exercised by tests. During the observation window it never fired because every round errored out at upstream auth. To exercise it in real-pre, the 40003 must be resolved.

### Q5. What is the stop reason distribution over the observation window?

**None observed.** All rounds returned `FETCH_ERROR` (inferred ‚Ä?the throw at `OrderSyncJob.java:137` matches the `FETCH_ERROR` path) before reaching the metric emission. No `EMPTY_PAGE / MAX_ORDERS / SINGLE_PAGE / NO_NEXT_CURSOR / MAX_PAGES / DUPLICATE_CURSOR` success-path reasons were recorded.

### Q6. Did `serviceFeeExpense` (DASH-RECON-MONEY-DRIFT-001) get disturbed by this change?

**No.** The order-sync change touches only the upstream call window and the metric emission; the `serviceFeeExpense` formula in `DataApplicationService` and the money-drift fix (commit `696cc902`) are not in the call path of `OrderSyncJob` or `OrderSyncService`. The full mvn test run (1760/1760) covered the dashboard money code path, and all pass. This task is orthogonal to DASH-RECON-MONEY-DRIFT-001.

### Q7. What is needed to unblock this and validate the SLA?

A credential refresh on the Douyin developer portal side. The local `DOUYIN_CLIENT_SECRET` (currently `7bc053a4-f905-484a-913f-c1c7714c1484` in `.env.real-pre`) is being rejected by ÊäñÂ∫ó upstream on every call ‚Ä?including the OAuth token refresh endpoint. Action items:
1. Open the ÊäñÂ∫ó open platform console for appId `7623665273727387199`.
2. Verify whether the app's client_secret has been rotated or revoked.
3. If rotated, regenerate, update `.env.real-pre` (`DOUYIN_CLIENT_SECRET`), restart the backend, and re-run preflight.
4. If the app is still authorized, check whether the account's `access_token` / `refresh_token` have been revoked, and re-run the OAuth authorize flow.
5. After credential restoration, restart the observation: hot task should emit metric log lines within 1 min, and `institute_hot_last_time` should advance every 60 s. Then re-run Q1/Q4/Q5 ‚Ä?expected p50 ‚â?30-60 s, p95 ‚â?120 s, p99 ‚â?300 s.

---

## 8. Decision

**Mark this task BLOCKED, not PASS.** Do not close the SLA target as met.

- Code/test/build/container gates: GREEN
- Runtime gate: BLOCKED on upstream 40003
- SLA evidence: cannot be collected without functioning upstream
- Recommended next action: unblock credentials per ¬ß7, then re-run the 10-15 min observation and re-evaluate Q1/Q4/Q5.

Per project CLAUDE.md: this is exactly the "Áº?Token„ÄÅÁº∫ÊéàÊùÉ / Áº∫ÁúüÂÆûËÆ¢Âç? BLOCKED scenario, and no mock data has been used to fake a pass.
