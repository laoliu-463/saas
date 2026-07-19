# Evidence Report — Talent Query Performance

## Metadata

- Time: 2026-07-13 21:08 Asia/Shanghai
- Environment: real-pre (local)
- Scope: full
- Branch: codex/ddd-user-role-application
- Code commit: 57e79387
- Worktree: dirty; unrelated concurrent OrderPayment/Harness/log files preserved
- Remote deploy: no

## Changes

- Backend list scan uses a bounded batch size of 200 instead of the requested page size.
- Talent detail reuses the preloaded claims and owner labels; duplicate claim lookup is removed.
- Frontend deduplicates identical pending list requests and discards stale responses.

## Verification

- Backend package: PASS (`mvn -f backend/pom.xml -DskipTests package`)
- Frontend build/type check: PASS (`npm ci` and `npm run build`)
- Backend talent regression: PASS (82 tests, 0 failures, 0 errors)
- Frontend talent regression: PASS (5 tests, 0 failures)
- Changed-file diff check: PASS
- Backend health: PASS, `GET /api/system/health` returned 200 / `UP`
- Frontend health: PASS, `/healthz` returned 200
- Docker: PASS, backend-real-pre and frontend-real-pre healthy after rebuild/restart

## Business Validation

- real-pre preflight: BLOCKED/PARTIAL
- Evidence: `runtime/qa/out/real-pre-preflight-20260713-210700/report.md`
- Admin login: FAIL, HTTP 401 after 5 attempts
- Database schema readiness: PASS
- Local real-pre data volume: 1 talent, 0 claims, 0 samples, 0 orders
- Authenticated `/api/talents` latency and production-volume query plan: not collected

## Conclusion

PARTIAL

The code-level regression and local container verification passed. Real production-volume speed improvement is not proven because real-pre authentication is unavailable and the local database has no representative talent data.

## Residual Risk

- Measure P50/P95 API latency and SQL/query counts with an authorized real-pre account and representative data.
- Recheck whether batch size 200 improves first-page latency after data volume is restored.
- Harness limits check remains FAIL because the pre-existing reports directory exceeds the 50-file limit and several historical reports exceed 200 lines; no unrelated cleanup was performed.
