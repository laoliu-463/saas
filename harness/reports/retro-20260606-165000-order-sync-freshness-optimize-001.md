# Retro — order-sync-freshness-optimize-001

- generatedAt: 2026-06-06T16:50:00+08:00
- task: ORDER-SYNC-FRESHNESS-OPTIMIZE-001
- environment: real-pre
- outcome: **BLOCKED** (code/test/build GREEN; runtime gate blocked by upstream 40003)
- companion: `order-sync-freshness-optimize-001-20260606-165000.md`, `evidence-20260606-165000-order-sync-freshness-optimize-001.md`

---

## 1. What went well

1. **Design held together end-to-end.** The split (HOT @ 1 min + RECENT @ 10 min, with separate Redis locks and a separate checkpoint) was implemented without touching the existing 10-min `INSTITUTE_RECENT` cron, satisfying the design constraint "must not change the 6468 large paging task to run every 1 min".
2. **Test-first gate stayed green.** 47/47 targeted + 1760/1760 full mvn test + `mvn package` all PASS. The hot-path metric log line was exercised by tests before the runtime gate was attempted.
3. **Safety gates held.** `safety-check.ps1` reported no forbidden scope drift; no dirty changes to `datasource/initial/`, `security/`, or `frontend/`; the prior `serviceFeeExpense` (DASH-RECON-MONEY-DRIFT-001) fix was not disturbed.
4. **Per-task locks are observably independent.** Both `institute_hot_lock` and `institute_recent_lock` showed TTL `-2` (not held) during the window, confirming the two chains do not deadlock.
5. **Honest reporting.** When the runtime 40003 was discovered, the run was downgraded to BLOCKED rather than papered over with mock data — per the project CLAUDE.md invariant.

## 2. What went wrong

1. **The 40003 is global, not local.** It fires for **every** Douyin endpoint we touch (order sync, institution info, activity list, product, **and** the OAuth token refresh endpoint). The signature rejection is upstream — the `DOUYIN_CLIENT_SECRET` in `.env.real-pre` is no longer accepted by 抖店.
2. **The preflight is blind to live auth state.** Preflight 8/8 PASS because it only inspects the locally cached token presence. It does not perform a live signed call, so it cannot catch a signature break. This is by design but is a real gap in the gate.
3. **The hot-task `freshnessLagSeconds` log line is success-path only.** It does not fire when the upstream throws. This is correct (you don't want a misleading lag number on a 40003), but it means the metric cannot be used to *quantify* a broken upstream — the log just goes silent. A future improvement would be to emit a "hot-failed" metric round on the error path so the operator can still see cadence vs. failures in a single grep.
4. **Circuit-breaker Redis keys were observed empty.** This is *probably* because the breaker opens for 5 min and then self-clears, but the exact key name and TTL behavior is not surfaced. Worth instrumenting if a future task depends on breaker state for orchestration.
5. **No automated gateway preflight in CI.** A live-signed `ping` against `buyin.institutionInfo` (read-only, low cost) would have caught the 40003 before deploy instead of after.

## 3. Root cause of the BLOCKED state

`code=40003 subCode=isv.signature-invalid` returned by 抖店 for appId `7623665273727387199` against every endpoint, including the OAuth token refresh. There are three plausible upstream causes; only the user can determine which:

| Hypothesis | How to verify |
|---|---|
| `DOUYIN_CLIENT_SECRET` was rotated by the 抖店 console, and the value in `.env.real-pre` is stale | Re-fetch the current secret from the 抖店开放平台 app console, compare with `.env.real-pre` |
| App access/refresh tokens were revoked (or account re-auth required) | In the 抖店 console, check whether the app is still "authorized" and re-run the OAuth grant flow if not |
| The app itself has been disabled / de-listed (e.g. quota exhausted, compliance issue) | Check the app's status in the 抖店 console; look for a notice from 抖店 in email / phone |

Until one of these is resolved on the user side, the local code cannot make any progress against the upstream — and per CLAUDE.md, we must not fake the SLA.

## 4. Recommended next steps

In strict order:

1. **User action (out-of-band):** open the 抖店开放平台 console for appId `7623665273727387199` and determine which of the three hypotheses above is true. Update `.env.real-pre` accordingly (`DOUYIN_CLIENT_SECRET`, or the new `DOUYIN_ACCESS_TOKEN` / `DOUYIN_REFRESH_TOKEN`).
2. **Restart backend:** `pwsh harness/restart-compose.ps1 -Env real-pre -Scope backend`.
3. **Re-run preflight** (`npm run e2e:real-pre:p0:preflight`) — should still PASS, but now the underlying state is healthy.
4. **Live ping:** add a one-off `curl`/Java probe to `buyin.institutionInfo` from inside the backend container to confirm the signature is accepted before the scheduled job runs.
5. **Re-observe 10-15 min** of hot task logs. Expected: at least 8-10 rounds of `freshnessLagSeconds` log lines, `institute_hot_last_time` advancing every 60 s, Q1 `pay_lag_sec_cst` dropping to ≤ 180 s, Q5 p95 ≤ 120 s.
6. **Re-run Q1/Q4/Q5** from §E.9. If all three pass, re-evaluate the §7 questions in the main report and re-mark as PASS.

## 5. What would change for re-verification

Once the credential state is restored, the only change needed is to **re-observe the runtime gate** (steps 1-6 above). No new code is required; the hot chain is already live, scheduled every 1 min, with the metric log line, separate lock, separate checkpoint, and safety caps in place. The new evidence will:

- Replace §5.1 of the main report with success-path log lines instead of 40003 errors.
- Replace §6 (Q1/Q4/Q5) with the new lag numbers.
- Flip §8 from BLOCKED to PASS (only if Q5 p95 ≤ 120 s and Q1 pay_lag_sec_cst ≤ 180 s).
- Update §7 answers from "Cannot be validated — BLOCKED" to the actual observed values.

The retro itself does not need to be rewritten unless the re-verification surfaces a new lesson (e.g. a different reason hot-chain latency is higher than expected).

## 6. What this retro does NOT cover

- The DASH-RECON-MONEY-DRIFT-001 fix (commit `696cc902`) is a separate, prior task and is not in scope here. Confirmed orthogonal to this change.
- Frontend / dashboard changes (recent-days popup, gross profit, refund reversal) are separate tasks and not affected.
- The `pick_source` / `pick_source_mapping` / 转链 flow is a separate domain; this task does not touch it.

## 7. Hand-off note

The current state of the branch is:

- `feature/auth-system` is dirty with hot-sync files uncommitted.
- The running real-pre backend (`saas-active-backend-real-pre-1`) has the hot chain **live and scheduled**, but it is currently emitting only 40003 errors. Container is healthy.
- The preflight gate is PASS but the upstream is not — do not interpret preflight as proof of healthy upstream.
- Recommended branch action: hold the hot-sync changes uncommitted (or stash) until the user-side credential step is complete, then re-deploy and re-verify rather than re-rolling.
